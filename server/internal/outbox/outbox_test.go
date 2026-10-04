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

func TestRepository_Enqueue_dedupesPendingMessages(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	messages := outbox.NewRepository(d.Reader)
	key := "push:" + uuid.NewV7().String()
	m := outbox.Message{Kind: outbox.KindPushHousehold, DedupeKey: &key, RunAt: now}

	// act
	d.InTx(t, func(tx tx.Tx) {
		require.NoError(t, messages.Bind(tx).Enqueue(t.Context(), m))
		require.NoError(t, messages.Bind(tx).Enqueue(t.Context(), m))
	})
	var pending int
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"SELECT count(*) FROM outbox_messages WHERE dedupe_key = $1", key).Scan(&pending))

	// act: once the pending one is gone, the key is free again
	d.InTx(t, func(tx tx.Tx) {
		claimed, err := messages.Bind(tx).Claim(t.Context(), now)
		require.NoError(t, err)
		require.NoError(t, messages.Bind(tx).Complete(t.Context(), claimed.ID))
		require.NoError(t, messages.Bind(tx).Enqueue(t.Context(), m))
	})
	var again int
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"SELECT count(*) FROM outbox_messages WHERE dedupe_key = $1", key).Scan(&again))

	// assert
	assert.Equal(t, 1, pending)
	assert.Equal(t, 1, again)
}

func TestRepository_Claim_oldestDueFirstAndLocked(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	messages := outbox.NewRepository(d.Reader)
	d.InTx(t, func(tx tx.Tx) {
		bound := messages.Bind(tx)
		require.NoError(t, bound.Enqueue(t.Context(), outbox.Message{Kind: "later", RunAt: now.Add(-time.Minute)}))
		require.NoError(t, bound.Enqueue(t.Context(), outbox.Message{Kind: "first", RunAt: now.Add(-time.Hour)}))
		require.NoError(t, bound.Enqueue(t.Context(), outbox.Message{Kind: "future", RunAt: now.Add(time.Hour)}))
	})

	// act & assert
	d.InTx(t, func(outer tx.Tx) {
		first, err := messages.Bind(outer).Claim(t.Context(), now)
		require.NoError(t, err)
		assert.Equal(t, outbox.Kind("first"), first.Kind)

		// another worker skips the locked row and gets the next due one
		d.InTx(t, func(inner tx.Tx) {
			second, err := messages.Bind(inner).Claim(t.Context(), now)
			require.NoError(t, err)
			assert.Equal(t, outbox.Kind("later"), second.Kind)

			// a third worker finds nothing: two rows are locked, the last is not due
			d.InTx(t, func(third tx.Tx) {
				_, err := messages.Bind(third).Claim(t.Context(), now)
				require.ErrorIs(t, err, aerrors.ErrNotFound)
			})
		})
	})
}

func TestRepository_RetryAndKill(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	messages := outbox.NewRepository(d.Reader)
	var id uuid.UUID
	d.InTx(t, func(tx tx.Tx) {
		require.NoError(t, messages.Bind(tx).Enqueue(t.Context(), outbox.Message{Kind: "flaky", RunAt: now}))
		m, err := messages.Bind(tx).Claim(t.Context(), now)
		require.NoError(t, err)
		id = m.ID
		require.NoError(t, messages.Bind(tx).Retry(t.Context(), id, now.Add(time.Minute), "boom"))
	})

	// act & assert: not due until run_at, then claimable with the attempt recorded
	d.InTx(t, func(tx tx.Tx) {
		_, err := messages.Bind(tx).Claim(t.Context(), now)
		require.ErrorIs(t, err, aerrors.ErrNotFound)
		m, err := messages.Bind(tx).Claim(t.Context(), now.Add(time.Minute))
		require.NoError(t, err)
		assert.Equal(t, int32(1), m.Attempts)
		require.NoError(t, messages.Bind(tx).Kill(t.Context(), id, "gave up"))
	})
	var status, lastError string
	var attempts int32
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"SELECT status, attempts, last_error FROM outbox_messages WHERE id = $1", id).Scan(&status, &attempts, &lastError))
	assert.Equal(t, "dead", status)
	assert.Equal(t, int32(2), attempts)
	assert.Equal(t, "gave up", lastError)
	d.InTx(t, func(tx tx.Tx) {
		_, err := messages.Bind(tx).Claim(t.Context(), now.Add(time.Hour))
		require.ErrorIs(t, err, aerrors.ErrNotFound, "dead messages are never claimed")
	})
}
