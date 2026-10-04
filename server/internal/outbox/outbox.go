package outbox

import (
	"context"
	"errors"
	"fmt"
	"time"
	"uuid"

	"github.com/jackc/pgx/v5"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/infra/storage/db"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/internal/outbox/queries"
)

type Kind string

const (
	KindPushHousehold    Kind = "push_household"
	KindRevokeAppleToken Kind = "revoke_apple_token"
)

type Message struct {
	ID        uuid.UUID
	Kind      Kind
	Payload   []byte
	DedupeKey *string
	RunAt     time.Time
	Attempts  int32
}

type Repository interface {
	// Enqueue is a no-op when a pending message with the same dedupe key exists.
	Enqueue(ctx context.Context, m Message) error
	// Claim locks the oldest due message until the transaction ends, so call it
	// on a bound repository and finish the message in the same transaction.
	Claim(ctx context.Context, now time.Time) (Message, error)
	Complete(ctx context.Context, id uuid.UUID) error
	Retry(ctx context.Context, id uuid.UUID, runAt time.Time, lastError string) error
	Kill(ctx context.Context, id uuid.UUID, lastError string) error
	Bind(tx tx.Tx) Repository
}

type repository struct {
	q *queries.Queries
}

var _ Repository = repository{}

func NewRepository(reader db.Reader) Repository {
	return repository{q: queries.New(reader)}
}

func (r repository) Bind(tx tx.Tx) Repository {
	return repository{q: queries.New(tx.DBTX())}
}

func (r repository) Enqueue(ctx context.Context, m Message) error {
	payload := m.Payload
	if payload == nil {
		payload = []byte("{}")
	}
	if err := r.q.EnqueueMessage(ctx, queries.EnqueueMessageParams{
		Kind:      string(m.Kind),
		Payload:   payload,
		DedupeKey: m.DedupeKey,
		RunAt:     m.RunAt,
	}); err != nil {
		return fmt.Errorf("enqueue message: %w", err)
	}
	return nil
}

func (r repository) Claim(ctx context.Context, now time.Time) (Message, error) {
	row, err := r.q.ClaimMessage(ctx, now)
	if errors.Is(err, pgx.ErrNoRows) {
		return Message{}, aerrors.NotFound("message")
	}
	if err != nil {
		return Message{}, fmt.Errorf("claim message: %w", err)
	}
	return Message{
		ID:        row.ID,
		Kind:      Kind(row.Kind),
		Payload:   row.Payload,
		DedupeKey: row.DedupeKey,
		RunAt:     row.RunAt,
		Attempts:  row.Attempts,
	}, nil
}

func (r repository) Complete(ctx context.Context, id uuid.UUID) error {
	if err := r.q.CompleteMessage(ctx, id); err != nil {
		return fmt.Errorf("complete message: %w", err)
	}
	return nil
}

func (r repository) Retry(ctx context.Context, id uuid.UUID, runAt time.Time, lastError string) error {
	if err := r.q.RetryMessage(ctx, queries.RetryMessageParams{ID: id, RunAt: runAt, LastError: &lastError}); err != nil {
		return fmt.Errorf("retry message: %w", err)
	}
	return nil
}

func (r repository) Kill(ctx context.Context, id uuid.UUID, lastError string) error {
	if err := r.q.KillMessage(ctx, queries.KillMessageParams{ID: id, LastError: &lastError}); err != nil {
		return fmt.Errorf("kill message: %w", err)
	}
	return nil
}
