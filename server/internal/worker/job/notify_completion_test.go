package job_test

import (
	"fmt"
	"testing"
	"time"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	hmodel "github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/infra/apns"
	"github.com/mickamy/LocateDo/internal/infra/fcm"
	"github.com/mickamy/LocateDo/internal/outbox"
	"github.com/mickamy/LocateDo/internal/worker/job"
	"github.com/mickamy/LocateDo/test/tdb"
	"github.com/mickamy/LocateDo/test/tseed"
)

func TestNotifyCompletion_sendsOneNoticePerDeviceInItsLanguage(t *testing.T) {
	t.Parallel()

	// arrange: the member checked off two of the owner's to-dos
	d := tdb.New(t)
	h, memberID := sharedHousehold(t, d)
	at := time.Now()
	completed(t, d, h, memberID, "Milk", at, "")
	completed(t, d, h, memberID, "Eggs", at.Add(time.Minute), "")
	noticeDevice(t, d, h.OwnerID, "ios", "production", "owner-phone", "ja", true)
	noticeDevice(t, d, h.OwnerID, "android", "", "owner-android", "en", true)
	noticeDevice(t, d, h.OwnerID, "ios", "production", "owner-ipad", "en", false)
	noticeDevice(t, d, memberID, "ios", "production", "member-phone", "en", true)
	ios := &fakePusher{}
	android := &fakeFCM{}

	// act
	err := noticeJob(d, ios, android).Handle(t.Context(), noticeMessage(h, memberID))

	// assert
	require.NoError(t, err)
	ja := "Alexが「Milk」ほか 1 件を完了しました" //nolint:gosmopolitan // the Japanese notice
	assert.Equal(t, []apns.CompletionNotice{{Body: ja, Count: 2}}, ios.completionNotices("owner-phone"))
	assert.Equal(t, []fcm.CompletionNotice{{Body: `Alex checked off "Milk" and 1 more`, Count: 2}},
		android.completionNotices("owner-android"))
	assert.Empty(t, ios.completionNotices("owner-ipad"), "its switch is off")
	assert.Empty(t, ios.completionNotices("member-phone"))
	assert.Equal(t, 2, rows(t, d, "SELECT count(*) FROM todo_completions WHERE notified_at IS NOT NULL"))
}

func TestNotifyCompletion_sendsNothing(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name    string
		arrange func(t *testing.T, d tdb.DB, h tseed.Household, memberID uuid.UUID)
	}{
		{
			name: "reopened before the notice",
			arrange: func(t *testing.T, d tdb.DB, h tseed.Household, memberID uuid.UUID) {
				completed(t, d, h, memberID, "Milk", time.Now(), "reopened")
			},
		},
		{
			name: "announced before",
			arrange: func(t *testing.T, d tdb.DB, h tseed.Household, memberID uuid.UUID) {
				id := completed(t, d, h, memberID, "Milk", time.Now(), "notified")
				_, err := d.Writer.Exec(t.Context(),
					"INSERT INTO todo_completions (todo_id, completer_id, completed_at) VALUES ($1, $2, now())",
					id, memberID)
				require.NoError(t, err)
			},
		},
		{
			name: "the creator left the household",
			arrange: func(t *testing.T, d tdb.DB, h tseed.Household, memberID uuid.UUID) {
				completed(t, d, h, memberID, "Milk", time.Now(), "")
				_, err := d.Writer.Exec(t.Context(), "DELETE FROM memberships WHERE user_id = $1", h.OwnerID)
				require.NoError(t, err)
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			// arrange
			d := tdb.New(t)
			h, memberID := sharedHousehold(t, d)
			tt.arrange(t, d, h, memberID)
			noticeDevice(t, d, h.OwnerID, "ios", "production", "owner-phone", "en", true)
			ios := &fakePusher{}

			// act
			err := noticeJob(d, ios, &fakeFCM{}).Handle(t.Context(), noticeMessage(h, memberID))

			// assert
			require.NoError(t, err)
			assert.Empty(t, ios.completionNotices("owner-phone"))
		})
	}
}

func TestNotifyCompletion_aFailedPushIsNotRetried(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	h, memberID := sharedHousehold(t, d)
	completed(t, d, h, memberID, "Milk", time.Now(), "")
	noticeDevice(t, d, h.OwnerID, "ios", "production", "owner-phone", "en", true)
	ios := &fakePusher{fail: map[string]error{"owner-phone": fmt.Errorf("%w: status 503", apns.ErrNotDelivered)}}
	sendNotice := noticeJob(d, ios, &fakeFCM{})
	require.NoError(t, sendNotice.Handle(t.Context(), noticeMessage(h, memberID)))

	// act
	ios.fail = nil
	err := sendNotice.Handle(t.Context(), noticeMessage(h, memberID))

	// assert
	require.NoError(t, err)
	assert.Empty(t, ios.completionNotices("owner-phone"), "the to-do was marked announced before the push")
}

func TestNotifyCompletion_forgetsUnregisteredDevices(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	h, memberID := sharedHousehold(t, d)
	completed(t, d, h, memberID, "Milk", time.Now(), "")
	noticeDevice(t, d, h.OwnerID, "android", "", "gone", "en", true)
	android := &fakeFCM{fail: map[string]error{"gone": fcm.ErrUnregistered}}

	// act
	err := noticeJob(d, &fakePusher{}, android).Handle(t.Context(), noticeMessage(h, memberID))

	// assert
	require.NoError(t, err)
	assert.Empty(t, tokens(t, d))
}

func sharedHousehold(t *testing.T, d tdb.DB) (tseed.Household, uuid.UUID) {
	t.Helper()

	h := d.Seeder.Household(t, hmodel.PlanPro)
	memberID := d.Seeder.Member(t, h.ID)
	_, err := d.Writer.Exec(t.Context(), "UPDATE users SET display_name = 'Alex' WHERE id = $1", memberID)
	require.NoError(t, err)
	return h, memberID
}

// completed seeds one of the owner's to-dos the completer checked off; state
// is "", "reopened", or "notified".
func completed(
	t *testing.T, d tdb.DB, h tseed.Household, completerID uuid.UUID, title string, at time.Time, state string,
) uuid.UUID {
	t.Helper()

	id := d.Seeder.Todo(t, h.ID, d.Seeder.Place(t, h.ID))
	_, err := d.Writer.Exec(t.Context(),
		"UPDATE todos SET title = $1, creator_id = $2, completed_at = $3 WHERE id = $4", title, h.OwnerID, at, id)
	require.NoError(t, err)
	_, err = d.Writer.Exec(t.Context(),
		`INSERT INTO todo_completions (todo_id, completer_id, completed_at, reopened_at, notified_at)
		 VALUES ($1, $2, $3, CASE WHEN $4 = 'reopened' THEN now() END, CASE WHEN $4 = 'notified' THEN now() END)`,
		id, completerID, at, state)
	require.NoError(t, err)
	return id
}

func noticeDevice(t *testing.T, d tdb.DB, userID uuid.UUID, platform, env, token, language string, on bool) {
	t.Helper()

	_, err := d.Writer.Exec(t.Context(),
		`INSERT INTO devices (user_id, platform, apns_environment, push_token, language, completion_notices)
		 VALUES ($1, $2, NULLIF($3, ''), $4, $5, $6)`, userID, platform, env, token, language, on)
	require.NoError(t, err)
}

func noticeJob(d tdb.DB, ios apns.Pusher, android fcm.Pusher) *job.NotifyCompletion {
	infra := d.Infra()
	infra.APNs = ios
	infra.FCM = android
	return job.NewNotifyCompletion(infra)
}

func noticeMessage(h tseed.Household, completerID uuid.UUID) outbox.Message {
	return outbox.NotifyCompletion(h.ID, h.OwnerID, completerID, time.Now())
}
