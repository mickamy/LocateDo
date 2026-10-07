package outbox_test

import (
	"testing"
	"time"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/internal/outbox"
	"github.com/mickamy/LocateDo/test/tdb"
)

var now = time.Date(2026, 10, 4, 12, 0, 0, 0, time.UTC)

const lease = 5 * time.Minute

func TestRepository_Enqueue_dedupesPendingMessages(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	messages := outbox.NewRepository(d.Reader)
	key := "push:" + uuid.NewV7().String()
	m := outbox.Message{Kind: outbox.KindPushHousehold, DedupeKey: &key, RunAt: now}

	// act
	enqueue(t, d, messages, m)
	enqueue(t, d, messages, m)

	// assert
	assert.Equal(t, 1, count(t, d, key))
}

func TestRepository_Enqueue_whileRunningQueuesAnother(t *testing.T) {
	t.Parallel()

	// arrange: the pending message is claimed, so it is running, not pending
	d := tdb.New(t)
	messages := outbox.NewRepository(d.Reader)
	key := "push:" + uuid.NewV7().String()
	m := outbox.Message{Kind: outbox.KindPushHousehold, DedupeKey: &key, RunAt: now}
	enqueue(t, d, messages, m)
	claimed := claim(t, d, messages, now)

	// act: a write lands while the claimed one is being delivered
	enqueue(t, d, messages, m)
	d.InTx(t, func(tx tx.Tx) {
		require.NoError(t, messages.Bind(tx).Complete(t.Context(), claimed.ID))
	})

	// assert: the write still gets its own delivery
	assert.Equal(t, 1, count(t, d, key))
	next := claim(t, d, messages, now)
	assert.NotEqual(t, claimed.ID, next.ID)
}

func TestRepository_Claim_oldestDueFirstAndLeased(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	messages := outbox.NewRepository(d.Reader)
	enqueue(t, d, messages, outbox.Message{Kind: "later", RunAt: now.Add(-time.Minute)})
	enqueue(t, d, messages, outbox.Message{Kind: "first", RunAt: now.Add(-time.Hour)})
	enqueue(t, d, messages, outbox.Message{Kind: "future", RunAt: now.Add(time.Hour)})

	// act & assert
	assert.Equal(t, outbox.Kind("first"), claim(t, d, messages, now).Kind)
	assert.Equal(t, outbox.Kind("later"), claim(t, d, messages, now).Kind, "a leased message is skipped")
	d.InTx(t, func(tx tx.Tx) {
		_, err := messages.Bind(tx).Claim(t.Context(), now, now.Add(lease))
		require.ErrorIs(t, err, aerrors.ErrNotFound, "two are leased, the last is not due")
	})
	assert.Equal(t, outbox.Kind("first"), claim(t, d, messages, now.Add(lease)).Kind,
		"an expired lease means its worker died, so the message is taken again")
}

func TestRepository_RetryAndKill(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	messages := outbox.NewRepository(d.Reader)
	enqueue(t, d, messages, outbox.Message{Kind: "flaky", RunAt: now})
	id := claim(t, d, messages, now).ID
	d.InTx(t, func(tx tx.Tx) {
		require.NoError(t, messages.Bind(tx).Retry(t.Context(), id, now.Add(time.Minute), "boom"))
	})

	// act & assert: not due until run_at, then claimable with the attempt recorded
	d.InTx(t, func(tx tx.Tx) {
		_, err := messages.Bind(tx).Claim(t.Context(), now, now.Add(lease))
		require.ErrorIs(t, err, aerrors.ErrNotFound)
	})
	m := claim(t, d, messages, now.Add(time.Minute))
	assert.Equal(t, int32(1), m.Attempts)
	d.InTx(t, func(tx tx.Tx) {
		require.NoError(t, messages.Bind(tx).Kill(t.Context(), id, now.Add(time.Minute), "gave up"))
	})
	var status, lastError string
	var attempts int32
	var deadAt time.Time
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"SELECT status, attempts, last_error, dead_at FROM outbox_messages WHERE id = $1", id).
		Scan(&status, &attempts, &lastError, &deadAt))
	assert.Equal(t, "dead", status)
	assert.Equal(t, int32(2), attempts)
	assert.Equal(t, "gave up", lastError)
	assert.WithinDuration(t, now.Add(time.Minute), deadAt, time.Millisecond)
	d.InTx(t, func(tx tx.Tx) {
		_, err := messages.Bind(tx).Claim(t.Context(), now.Add(time.Hour), now.Add(time.Hour+lease))
		require.ErrorIs(t, err, aerrors.ErrNotFound, "dead messages are never claimed")
	})
}

func TestRepository_Retry_dropsWhenAnotherIsPending(t *testing.T) {
	t.Parallel()

	// arrange: a write queued a fresh message while this one was being delivered
	d := tdb.New(t)
	messages := outbox.NewRepository(d.Reader)
	key := "push:" + uuid.NewV7().String()
	m := outbox.Message{Kind: outbox.KindPushHousehold, DedupeKey: &key, RunAt: now}
	enqueue(t, d, messages, m)
	failed := claim(t, d, messages, now)
	enqueue(t, d, messages, m)

	// act
	d.InTx(t, func(tx tx.Tx) {
		require.NoError(t, messages.Bind(tx).Retry(t.Context(), failed.ID, now.Add(time.Minute), "boom"))
	})

	// assert: the fresh one covers the work
	assert.Equal(t, 1, count(t, d, key))
	assert.NotEqual(t, failed.ID, claim(t, d, messages, now).ID)
}

func TestRepository_Health(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	messages := outbox.NewRepository(d.Reader)
	for _, q := range []struct {
		sql  string
		args []any
	}{
		{"INSERT INTO outbox_messages (kind, run_at, created_at) VALUES ('due', $1, $2)",
			[]any{now.Add(-10 * time.Minute), now.Add(-time.Hour)}},
		{"INSERT INTO outbox_messages (kind, run_at, created_at) VALUES ('retrying', $1, $2)",
			[]any{now.Add(time.Hour), now.Add(-7 * time.Hour)}},
		{"INSERT INTO outbox_messages (kind, status, lease_until, created_at) VALUES ('stuck', 'running', $1, $2)",
			[]any{now.Add(-20 * time.Minute), now.Add(-30 * time.Minute)}},
		{"INSERT INTO outbox_messages (kind, status, dead_at, created_at) VALUES ('recent', 'dead', $1, $2)",
			[]any{now.Add(-30 * time.Minute), now.Add(-20 * time.Hour)}},
		{"INSERT INTO outbox_messages (kind, status, dead_at, created_at) VALUES ('old', 'dead', $1, $2)",
			[]any{now.Add(-2 * time.Hour), now.Add(-20 * time.Hour)}},
	} {
		_, err := d.Writer.Exec(t.Context(), q.sql, q.args...)
		require.NoError(t, err)
	}

	// act
	got, err := messages.Health(t.Context(), now, now.Add(-time.Hour))

	// assert: the expired lease is the most overdue, the retrying message the oldest, dead ones are left out
	require.NoError(t, err)
	assert.Equal(t, outbox.Health{Overdue: 20 * time.Minute, Oldest: 7 * time.Hour, Dead: 1}, got)
}

func TestRepository_Health_empty(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)

	// act
	got, err := outbox.NewRepository(d.Reader).Health(t.Context(), now, now.Add(-time.Hour))

	// assert
	require.NoError(t, err)
	assert.Equal(t, outbox.Health{}, got)
}

func enqueue(t *testing.T, d tdb.DB, messages outbox.Repository, m outbox.Message) {
	t.Helper()

	d.InTx(t, func(tx tx.Tx) {
		require.NoError(t, messages.Bind(tx).Enqueue(t.Context(), m))
	})
}

func claim(t *testing.T, d tdb.DB, messages outbox.Repository, at time.Time) outbox.Message {
	t.Helper()

	var m outbox.Message
	d.InTx(t, func(tx tx.Tx) {
		var err error
		m, err = messages.Bind(tx).Claim(t.Context(), at, at.Add(lease))
		require.NoError(t, err)
	})
	return m
}

func count(t *testing.T, d tdb.DB, key string) int {
	t.Helper()

	var n int
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"SELECT count(*) FROM outbox_messages WHERE dedupe_key = $1", key).Scan(&n))
	return n
}
