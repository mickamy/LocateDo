package worker_test

import (
	"context"
	"errors"
	"testing"
	"time"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/internal/outbox"
	"github.com/mickamy/LocateDo/internal/worker"
	"github.com/mickamy/LocateDo/test/tdb"
)

var now = time.Date(2026, 10, 4, 12, 0, 0, 0, time.UTC)

func TestConsumer_Step_deliversAndCompletes(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	messages := outbox.NewRepository(d.Reader)
	var seen [][]byte
	handlers := worker.Handlers{
		"greet": outbox.HandlerFunc(func(_ context.Context, m outbox.Message) error {
			seen = append(seen, m.Payload)
			return nil
		}),
	}
	enqueue(t, d, messages, outbox.Message{Kind: "greet", Payload: []byte(`{"to":"a"}`), RunAt: now})
	enqueue(t, d, messages, outbox.Message{Kind: "greet", Payload: []byte(`{"to":"b"}`), RunAt: now.Add(time.Second)})
	consumer := worker.NewConsumer(d.Transactor, messages, handlers)
	ctx := clock.Set(t.Context(), clock.NewFixed(now.Add(time.Minute)))

	// act
	first, err := consumer.Step(ctx)
	require.NoError(t, err)
	second, err := consumer.Step(ctx)
	require.NoError(t, err)
	third, err := consumer.Step(ctx)
	require.NoError(t, err)

	// assert
	assert.True(t, first)
	assert.True(t, second)
	assert.False(t, third, "nothing left")
	assert.Equal(t, [][]byte{[]byte(`{"to": "a"}`), []byte(`{"to": "b"}`)}, seen, "oldest first")
	assert.Zero(t, count(t, d))
}

func TestConsumer_Step_retriesThenGivesUp(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	messages := outbox.NewRepository(d.Reader)
	handlers := worker.Handlers{
		"flaky": outbox.HandlerFunc(func(context.Context, outbox.Message) error { return errors.New("boom") }),
	}
	enqueue(t, d, messages, outbox.Message{Kind: "flaky", RunAt: now})
	consumer := worker.NewConsumer(d.Transactor, messages, handlers)

	// act: the first failure schedules a retry 20 s out
	delivered, err := consumer.Step(clock.Set(t.Context(), clock.NewFixed(now)))
	require.NoError(t, err)
	require.True(t, delivered)
	status, attempts, runAt := message(t, d)
	assert.Equal(t, "pending", status)
	assert.Equal(t, int32(1), attempts)
	assert.True(t, now.Add(20*time.Second).Equal(runAt), "10 s << 1")

	tooEarly, err := consumer.Step(clock.Set(t.Context(), clock.NewFixed(now.Add(10*time.Second))))
	require.NoError(t, err)
	assert.False(t, tooEarly)

	// act: keep failing until the limit
	_, err = d.Writer.Exec(t.Context(), "UPDATE outbox_messages SET attempts = $1", worker.MaxAttempts-1)
	require.NoError(t, err)
	delivered, err = consumer.Step(clock.Set(t.Context(), clock.NewFixed(now.Add(time.Hour))))
	require.NoError(t, err)
	require.True(t, delivered)

	// assert
	status, attempts, _ = message(t, d)
	assert.Equal(t, "dead", status)
	assert.Equal(t, int32(worker.MaxAttempts), attempts)
}

func TestConsumer_Step_unknownKindIsDead(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	messages := outbox.NewRepository(d.Reader)
	enqueue(t, d, messages, outbox.Message{Kind: "mystery", RunAt: now})
	consumer := worker.NewConsumer(d.Transactor, messages, worker.Handlers{})

	// act
	delivered, err := consumer.Step(clock.Set(t.Context(), clock.NewFixed(now)))

	// assert
	require.NoError(t, err)
	assert.True(t, delivered)
	status, _, _ := message(t, d)
	assert.Equal(t, "dead", status)
}

func enqueue(t *testing.T, d tdb.DB, messages outbox.Repository, m outbox.Message) {
	t.Helper()

	d.InTx(t, func(tx tx.Tx) {
		require.NoError(t, messages.Bind(tx).Enqueue(t.Context(), m))
	})
}

func count(t *testing.T, d tdb.DB) int {
	t.Helper()

	var n int
	require.NoError(t, d.Writer.QueryRow(t.Context(), "SELECT count(*) FROM outbox_messages").Scan(&n))
	return n
}

func message(t *testing.T, d tdb.DB) (string, int32, time.Time) {
	t.Helper()

	var id uuid.UUID
	var status string
	var attempts int32
	var runAt time.Time
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"SELECT id, status, attempts, run_at FROM outbox_messages").Scan(&id, &status, &attempts, &runAt))
	return status, attempts, runAt
}
