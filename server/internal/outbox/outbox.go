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
	KindSyncEntitlement  Kind = "sync_entitlement"
)

// PushHousehold matches what the sync triggers enqueue, for writes that
// change what devices see without touching a synced row.
func PushHousehold(householdID uuid.UUID, now time.Time) Message {
	key := "push:" + householdID.String()
	return Message{
		Kind:      KindPushHousehold,
		Payload:   []byte(`{"household_id":"` + householdID.String() + `"}`),
		DedupeKey: &key,
		RunAt:     now,
	}
}

// SyncEntitlement asks the worker to set the plan of the user's household
// from their current RevenueCat entitlement.
func SyncEntitlement(userID uuid.UUID, now time.Time) Message {
	key := "entitlement:" + userID.String()
	return Message{
		Kind:      KindSyncEntitlement,
		Payload:   []byte(`{"user_id":"` + userID.String() + `"}`),
		DedupeKey: &key,
		RunAt:     now,
	}
}

// DeadRetention is how long a dead message is kept for inspection.
const DeadRetention = 7 * 24 * time.Hour

type Message struct {
	ID        uuid.UUID
	Kind      Kind
	Payload   []byte
	DedupeKey *string
	RunAt     time.Time
	Attempts  int32
	CreatedAt time.Time
}

// Handler delivers one kind of message. An error schedules a retry.
type Handler interface {
	Handle(ctx context.Context, m Message) error
}

type HandlerFunc func(ctx context.Context, m Message) error

func (f HandlerFunc) Handle(ctx context.Context, m Message) error {
	return f(ctx, m)
}

type Repository interface {
	// Enqueue is a no-op when a pending message with the same dedupe key exists.
	Enqueue(ctx context.Context, m Message) error
	// Claim leases the oldest due message, or one whose lease expired, until
	// leaseUntil. The lease is what keeps other workers off it, so the claim can
	// commit before the message is delivered.
	Claim(ctx context.Context, now, leaseUntil time.Time) (Message, error)
	Complete(ctx context.Context, id uuid.UUID) error
	// Retry returns the message to the queue, or drops it when a pending
	// message with the same dedupe key arrived meanwhile and will do the work.
	Retry(ctx context.Context, id uuid.UUID, runAt time.Time, lastError string) error
	Kill(ctx context.Context, id uuid.UUID, lastError string) error
	// SweepDead removes dead messages created before the cutoff and reports how many.
	SweepDead(ctx context.Context, before time.Time) (int, error)
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

func (r repository) Claim(ctx context.Context, now, leaseUntil time.Time) (Message, error) {
	row, err := r.q.ClaimMessage(ctx, queries.ClaimMessageParams{Now: now, LeaseUntil: &leaseUntil})
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
		CreatedAt: row.CreatedAt,
	}, nil
}

func (r repository) Complete(ctx context.Context, id uuid.UUID) error {
	if err := r.q.CompleteMessage(ctx, id); err != nil {
		return fmt.Errorf("complete message: %w", err)
	}
	return nil
}

func (r repository) Retry(ctx context.Context, id uuid.UUID, runAt time.Time, lastError string) error {
	n, err := r.q.RetryMessage(ctx, queries.RetryMessageParams{ID: id, RunAt: runAt, LastError: &lastError})
	if err != nil {
		return fmt.Errorf("retry message: %w", err)
	}
	if n == 0 {
		return r.Complete(ctx, id)
	}
	return nil
}

func (r repository) Kill(ctx context.Context, id uuid.UUID, lastError string) error {
	if err := r.q.KillMessage(ctx, queries.KillMessageParams{ID: id, LastError: &lastError}); err != nil {
		return fmt.Errorf("kill message: %w", err)
	}
	return nil
}

func (r repository) SweepDead(ctx context.Context, before time.Time) (int, error) {
	n, err := r.q.SweepDeadMessages(ctx, before)
	if err != nil {
		return 0, fmt.Errorf("sweep dead messages: %w", err)
	}
	return int(n), nil
}
