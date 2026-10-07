package job_test

import (
	"context"
	"encoding/json"
	"errors"
	"slices"
	"sync"
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
)

func TestPushHousehold_wakesEveryMembersDeviceOnItsPlatform(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	h := d.Seeder.Household(t, hmodel.PlanPro)
	memberID := d.Seeder.Member(t, h.ID)
	device(t, d, h.OwnerID, "ios", "production", "owner-phone")
	device(t, d, memberID, "ios", "sandbox", "member-phone")
	device(t, d, memberID, "android", "", "member-android")
	outsider := d.Seeder.Household(t, hmodel.PlanPro)
	device(t, d, outsider.OwnerID, "ios", "production", "stranger-phone")
	device(t, d, outsider.OwnerID, "android", "", "stranger-android")
	pusher := &fakePusher{}
	android := &fakeFCM{}

	// act
	err := pushJob(d, pusher, android).Handle(t.Context(), pushMessage(t, h.ID))

	// assert
	require.NoError(t, err)
	assert.ElementsMatch(t, []string{"owner-phone", "member-phone"}, pusher.woken())
	assert.Equal(t, apns.Production, pusher.environment("owner-phone"))
	assert.Equal(t, apns.Sandbox, pusher.environment("member-phone"))
	assert.Equal(t, []string{"member-android"}, android.woken())
}

func TestPushHousehold_skipsAnonymousDevices(t *testing.T) {
	t.Parallel()

	// arrange: devices consenting to promotions without a user belong to no household
	d := tdb.New(t)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	device(t, d, h.OwnerID, "ios", "production", "owner-phone")
	anonymousDevice(t, d, "ios", "production", "anonymous-phone")
	anonymousDevice(t, d, "android", "", "anonymous-android")
	pusher := &fakePusher{}
	android := &fakeFCM{}

	// act
	err := pushJob(d, pusher, android).Handle(t.Context(), pushMessage(t, h.ID))

	// assert
	require.NoError(t, err)
	assert.Equal(t, []string{"owner-phone"}, pusher.woken())
	assert.Empty(t, android.woken())
}

func TestPushHousehold_forgetsUnregisteredAndroidTokens(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	device(t, d, h.OwnerID, "android", "", "stale-android")
	device(t, d, h.OwnerID, "android", "", "fresh-android")
	device(t, d, h.OwnerID, "ios", "production", "stale-android")
	android := &fakeFCM{fail: map[string]error{"stale-android": fcm.ErrUnregistered}}

	// act
	err := pushJob(d, &fakePusher{}, android).Handle(t.Context(), pushMessage(t, h.ID))

	// assert
	require.NoError(t, err)
	assert.ElementsMatch(t, []string{"fresh-android", "stale-android"}, tokens(t, d),
		"only the Android registration is forgotten")
}

func TestPushHousehold_forgetsUnregisteredTokens(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	device(t, d, h.OwnerID, "ios", "production", "stale")
	device(t, d, h.OwnerID, "ios", "production", "fresh")
	pusher := &fakePusher{fail: map[string]error{"stale": apns.ErrUnregistered}}

	// act
	err := pushJob(d, pusher, &fakeFCM{}).Handle(t.Context(), pushMessage(t, h.ID))

	// assert
	require.NoError(t, err, "an unregistered token is not a failure")
	assert.Equal(t, []string{"fresh"}, tokens(t, d))
}

func TestPushHousehold_retriesOtherFailures(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	device(t, d, h.OwnerID, "ios", "production", "flaky")
	device(t, d, h.OwnerID, "ios", "production", "fine")
	pusher := &fakePusher{fail: map[string]error{"flaky": errors.New("status 503")}}

	// act
	err := pushJob(d, pusher, &fakeFCM{}).Handle(t.Context(), pushMessage(t, h.ID))

	// assert
	require.ErrorContains(t, err, "503")
	assert.Contains(t, pusher.woken(), "fine", "the other devices are still woken")
	assert.ElementsMatch(t, []string{"flaky", "fine"}, tokens(t, d), "nothing is forgotten")
}

func TestSyncTriggers_enqueueOnePendingPushPerHousehold(t *testing.T) {
	t.Parallel()

	// arrange & act: several writes and a delete in one household, one write in another
	d := tdb.New(t)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	placeID := d.Seeder.Place(t, h.ID)
	todoID := d.Seeder.Todo(t, h.ID, placeID)
	_, err := d.Writer.Exec(t.Context(), "DELETE FROM todos WHERE id = $1", todoID)
	require.NoError(t, err)
	other := d.Seeder.Household(t, hmodel.PlanFree)

	// assert
	var households []uuid.UUID
	rows, err := d.Writer.Query(t.Context(),
		"SELECT (payload->>'household_id')::uuid FROM outbox_messages WHERE kind = 'push_household' AND status = 'pending'")
	require.NoError(t, err)
	for rows.Next() {
		var id uuid.UUID
		require.NoError(t, rows.Scan(&id))
		households = append(households, id)
	}
	require.NoError(t, rows.Err())
	assert.ElementsMatch(t, []uuid.UUID{h.ID, other.ID}, households)
}

type fakePusher struct {
	fail map[string]error

	mu   sync.Mutex
	woke []string
	envs map[string]apns.Environment
}

var _ apns.Pusher = (*fakePusher)(nil)

func (f *fakePusher) Wake(_ context.Context, env apns.Environment, token string, _ time.Time) error {
	if err := f.fail[token]; err != nil {
		return err
	}
	f.mu.Lock()
	defer f.mu.Unlock()
	f.woke = append(f.woke, token)
	if f.envs == nil {
		f.envs = map[string]apns.Environment{}
	}
	f.envs[token] = env
	return nil
}

func (f *fakePusher) woken() []string {
	f.mu.Lock()
	defer f.mu.Unlock()
	return slices.Clone(f.woke)
}

func (f *fakePusher) environment(token string) apns.Environment {
	f.mu.Lock()
	defer f.mu.Unlock()
	return f.envs[token]
}

type fakeFCM struct {
	fail map[string]error

	mu   sync.Mutex
	woke []string
}

var _ fcm.Pusher = (*fakeFCM)(nil)

func (f *fakeFCM) Wake(_ context.Context, token string, _ time.Time) error {
	if err := f.fail[token]; err != nil {
		return err
	}
	f.mu.Lock()
	defer f.mu.Unlock()
	f.woke = append(f.woke, token)
	return nil
}

func (f *fakeFCM) woken() []string {
	f.mu.Lock()
	defer f.mu.Unlock()
	return slices.Clone(f.woke)
}

func pushJob(d tdb.DB, ios apns.Pusher, android fcm.Pusher) *job.PushHousehold {
	infra := d.Infra()
	infra.APNs = ios
	infra.FCM = android
	return job.NewPushHousehold(infra)
}

func pushMessage(t *testing.T, householdID uuid.UUID) outbox.Message {
	t.Helper()

	payload, err := json.Marshal(map[string]uuid.UUID{"household_id": householdID})
	require.NoError(t, err)
	return outbox.Message{Kind: outbox.KindPushHousehold, Payload: payload}
}

func device(t *testing.T, d tdb.DB, userID uuid.UUID, platform, env, token string) {
	t.Helper()

	_, err := d.Writer.Exec(t.Context(),
		"INSERT INTO devices (user_id, platform, apns_environment, push_token) VALUES ($1, $2, NULLIF($3, ''), $4)",
		userID, platform, env, token)
	require.NoError(t, err)
}

func anonymousDevice(t *testing.T, d tdb.DB, platform, env, token string) {
	t.Helper()

	_, err := d.Writer.Exec(t.Context(),
		`INSERT INTO devices (platform, apns_environment, push_token, promotions_consented_at)
		 VALUES ($1, NULLIF($2, ''), $3, now())`, platform, env, token)
	require.NoError(t, err)
}

func tokens(t *testing.T, d tdb.DB) []string {
	t.Helper()

	rows, err := d.Writer.Query(t.Context(), "SELECT push_token FROM devices ORDER BY push_token")
	require.NoError(t, err)
	defer rows.Close()
	var out []string
	for rows.Next() {
		var token string
		require.NoError(t, rows.Scan(&token))
		out = append(out, token)
	}
	require.NoError(t, rows.Err())
	return out
}
