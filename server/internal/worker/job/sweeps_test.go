package job_test

import (
	"testing"
	"time"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	dmodel "github.com/mickamy/LocateDo/internal/feature/device/model"
	hmodel "github.com/mickamy/LocateDo/internal/feature/household/model"
	smodel "github.com/mickamy/LocateDo/internal/feature/sync/model"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/internal/outbox"
	"github.com/mickamy/LocateDo/internal/worker/job"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestSweepTombstones_Run(t *testing.T) {
	t.Parallel()

	// arrange: one tombstone past retention, one within it
	d := tdb.New(t)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	placeID := d.Seeder.Place(t, h.ID)
	old := d.Seeder.Todo(t, h.ID, placeID)
	fresh := d.Seeder.Todo(t, h.ID, placeID)
	for _, id := range []uuid.UUID{old, fresh} {
		_, err := d.Writer.Exec(t.Context(), "DELETE FROM todos WHERE id = $1", id)
		require.NoError(t, err)
	}
	now := time.Now()
	_, err := d.Writer.Exec(t.Context(), "UPDATE deletions SET deleted_at = $1 WHERE row_id = $2",
		now.Add(-smodel.TombstoneRetention-time.Hour), old)
	require.NoError(t, err)

	// act
	err = job.NewSweepTombstones(d.Infra()).Run(clock.Set(t.Context(), clock.NewFixed(now)))

	// assert
	require.NoError(t, err)
	assert.Equal(t, 1, rows(t, d, "SELECT count(*) FROM deletions WHERE household_id = $1", h.ID))
	assert.Positive(t, rows(t, d, "SELECT swept_version FROM households WHERE id = $1", h.ID))
}

func TestSweepRefreshTokens_Run(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	userID := d.Seeder.User(t)
	now := time.Now()
	for i, expiresAt := range []time.Time{now.Add(-time.Minute), now.Add(time.Hour)} {
		_, err := d.Writer.Exec(t.Context(),
			"INSERT INTO refresh_tokens (user_id, family_id, token_hash, expires_at) VALUES ($1, $2, $3, $4)",
			userID, uuid.NewV7(), []byte{byte(i)}, expiresAt)
		require.NoError(t, err)
	}

	// act
	err := job.NewSweepRefreshTokens(d.Infra()).Run(clock.Set(t.Context(), clock.NewFixed(now)))

	// assert
	require.NoError(t, err)
	assert.Equal(t, 1, rows(t, d, "SELECT count(*) FROM refresh_tokens WHERE user_id = $1", userID))
}

func TestSweepAnonymousDevices_Run(t *testing.T) {
	t.Parallel()

	// arrange: an anonymous device past retention, one within it, and an owned one past it
	d := tdb.New(t)
	now := time.Now()
	stale := now.Add(-dmodel.AnonymousRetention - time.Hour)
	for _, dev := range []struct {
		userID     *uuid.UUID
		token      string
		lastSeenAt time.Time
	}{
		{nil, "stale", stale},
		{nil, "fresh", now.Add(-time.Hour)},
		{new(d.Seeder.User(t)), "owned", stale},
	} {
		_, err := d.Writer.Exec(t.Context(),
			"INSERT INTO devices (user_id, platform, push_token, last_seen_at) VALUES ($1, 'android', $2, $3)",
			dev.userID, dev.token, dev.lastSeenAt)
		require.NoError(t, err)
	}

	// act
	err := job.NewSweepAnonymousDevices(d.Infra()).Run(clock.Set(t.Context(), clock.NewFixed(now)))

	// assert
	require.NoError(t, err)
	assert.Equal(t, []string{"fresh", "owned"}, tokens(t, d))
}

func TestSweepDeadMessages_Run(t *testing.T) {
	t.Parallel()

	// arrange: one died past retention, one recently after a long retry, and an old pending one
	d := tdb.New(t)
	now := time.Now()
	old := now.Add(-outbox.DeadRetention - time.Hour)
	for _, m := range []struct {
		status    string
		deadAt    *time.Time
		createdAt time.Time
	}{
		{"dead", &old, old},
		{"dead", new(now.Add(-time.Hour)), old},
		{"pending", nil, old},
	} {
		_, err := d.Writer.Exec(t.Context(),
			"INSERT INTO outbox_messages (kind, status, dead_at, created_at) VALUES ('x', $1, $2, $3)",
			m.status, m.deadAt, m.createdAt)
		require.NoError(t, err)
	}

	// act
	err := job.NewSweepDeadMessages(d.Infra()).Run(clock.Set(t.Context(), clock.NewFixed(now)))

	// assert
	require.NoError(t, err)
	assert.Equal(t, 2, rows(t, d, "SELECT count(*) FROM outbox_messages"))
}

func rows(t *testing.T, d tdb.DB, query string, args ...any) int {
	t.Helper()

	var n int
	require.NoError(t, d.Writer.QueryRow(t.Context(), query, args...).Scan(&n))
	return n
}
