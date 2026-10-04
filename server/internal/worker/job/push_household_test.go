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
	"github.com/mickamy/LocateDo/internal/outbox"
	"github.com/mickamy/LocateDo/internal/worker/job"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestPushHousehold_wakesEveryMembersIOSDevice(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	h := d.Seeder.Household(t, hmodel.PlanPro)
	memberID := d.Seeder.Member(t, h.ID)
	device(t, d, h.OwnerID, "ios", "owner-phone")
	device(t, d, memberID, "ios", "member-phone")
	device(t, d, memberID, "android", "member-android")
	outsider := d.Seeder.Household(t, hmodel.PlanPro)
	device(t, d, outsider.OwnerID, "ios", "stranger-phone")
	pusher := &fakePusher{}

	// act
	err := pushJob(d, pusher).Handle(t.Context(), pushMessage(t, h.ID))

	// assert
	require.NoError(t, err)
	assert.ElementsMatch(t, []string{"owner-phone", "member-phone"}, pusher.woken())
}

func TestPushHousehold_forgetsUnregisteredTokens(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	device(t, d, h.OwnerID, "ios", "stale")
	device(t, d, h.OwnerID, "ios", "fresh")
	pusher := &fakePusher{fail: map[string]error{"stale": apns.ErrUnregistered}}

	// act
	err := pushJob(d, pusher).Handle(t.Context(), pushMessage(t, h.ID))

	// assert
	require.NoError(t, err, "an unregistered token is not a failure")
	assert.Equal(t, []string{"fresh"}, tokens(t, d))
}

func TestPushHousehold_retriesOtherFailures(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	device(t, d, h.OwnerID, "ios", "flaky")
	device(t, d, h.OwnerID, "ios", "fine")
	pusher := &fakePusher{fail: map[string]error{"flaky": errors.New("status 503")}}

	// act
	err := pushJob(d, pusher).Handle(t.Context(), pushMessage(t, h.ID))

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
}

var _ apns.Pusher = (*fakePusher)(nil)

func (f *fakePusher) Wake(_ context.Context, token string, _ time.Time) error {
	if err := f.fail[token]; err != nil {
		return err
	}
	f.mu.Lock()
	defer f.mu.Unlock()
	f.woke = append(f.woke, token)
	return nil
}

func (f *fakePusher) woken() []string {
	f.mu.Lock()
	defer f.mu.Unlock()
	return slices.Clone(f.woke)
}

func pushJob(d tdb.DB, pusher apns.Pusher) *job.PushHousehold {
	infra := d.Infra()
	infra.APNs = pusher
	return job.NewPushHousehold(infra)
}

func pushMessage(t *testing.T, householdID uuid.UUID) outbox.Message {
	t.Helper()

	payload, err := json.Marshal(map[string]uuid.UUID{"household_id": householdID})
	require.NoError(t, err)
	return outbox.Message{Kind: outbox.KindPushHousehold, Payload: payload}
}

func device(t *testing.T, d tdb.DB, userID uuid.UUID, platform, token string) {
	t.Helper()

	_, err := d.Writer.Exec(t.Context(),
		"INSERT INTO devices (user_id, platform, push_token) VALUES ($1, $2, $3)", userID, platform, token)
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
