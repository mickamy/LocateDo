package worker

import (
	"context"
	"errors"
	"fmt"
	"time"
	"uuid"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/internal/lib/execution"
	"github.com/mickamy/LocateDo/internal/lib/logger"
	"github.com/mickamy/LocateDo/internal/outbox"
	"github.com/mickamy/LocateDo/internal/worker/job"
)

const (
	PollInterval = time.Second
	MaxAttempts  = 20
	// Lease outlasts HandleTimeout, so a live worker always finishes before
	// another one may take the message over.
	Lease         = 5 * time.Minute
	HandleTimeout = 2 * time.Minute
	baseBackoff   = 10 * time.Second
	maxBackoff    = time.Hour
)

var errUnknownKind = errors.New("no handler for this kind")

type Handlers map[outbox.Kind]outbox.Handler

func NewHandlers(
	pushHousehold *job.PushHousehold,
	revokeAppleToken *job.RevokeAppleToken,
	syncEntitlement *job.SyncEntitlement,
) Handlers {
	return Handlers{
		outbox.KindPushHousehold:    pushHousehold,
		outbox.KindRevokeAppleToken: revokeAppleToken,
		outbox.KindSyncEntitlement:  syncEntitlement,
	}
}

// Consumer delivers outbox messages one at a time. A claim leases the message
// and commits at once; delivery runs outside any transaction, so writes made
// meanwhile queue a fresh message instead of being folded into this one. A
// worker that dies mid-delivery leaves the lease to run out, and the message
// is claimed again.
type Consumer struct {
	transactor tx.Transactor
	messages   outbox.Repository
	handlers   Handlers
}

func NewConsumer(transactor tx.Transactor, messages outbox.Repository, handlers Handlers) Consumer {
	return Consumer{transactor: transactor, messages: messages, handlers: handlers}
}

// Run keeps delivering until ctx ends, resting only when nothing is due.
func (c Consumer) Run(ctx context.Context) {
	for {
		delivered, err := c.Step(ctx)
		if err != nil {
			logger.Error(ctx, "outbox step failed", "error", err)
		}
		if delivered && err == nil {
			continue
		}
		select {
		case <-ctx.Done():
			return
		case <-time.After(PollInterval):
		}
	}
}

// Step claims and delivers one due message; it reports whether there was one.
func (c Consumer) Step(ctx context.Context) (bool, error) {
	now := clock.Now(ctx)
	var m outbox.Message
	err := c.transactor.WithTx(ctx, func(tx tx.Tx) error {
		var err error
		if m, err = c.messages.Bind(tx).Claim(ctx, now, now.Add(Lease)); err != nil {
			return fmt.Errorf("claim: %w", err)
		}
		return nil
	})
	if errors.Is(err, aerrors.ErrNotFound) {
		return false, nil
	}
	if err != nil {
		return false, fmt.Errorf("step: %w", err)
	}

	ctx = execution.SetID(execution.SetJobName(ctx, string(m.Kind)), uuid.NewV7())
	handleErr := c.handle(ctx, m)
	if err := c.transactor.WithTx(ctx, func(tx tx.Tx) error {
		return c.finish(ctx, c.messages.Bind(tx), m, handleErr)
	}); err != nil {
		return true, fmt.Errorf("finish: %w", err)
	}
	return true, nil
}

func (c Consumer) handle(ctx context.Context, m outbox.Message) error {
	handler, ok := c.handlers[m.Kind]
	if !ok {
		return fmt.Errorf("%w: %s", errUnknownKind, m.Kind)
	}
	ctx, cancel := context.WithTimeout(ctx, HandleTimeout)
	defer cancel()
	if err := handler.Handle(ctx, m); err != nil {
		return fmt.Errorf("handle %s: %w", m.Kind, err)
	}
	return nil
}

func (c Consumer) finish(ctx context.Context, messages outbox.Repository, m outbox.Message, handleErr error) error {
	if handleErr == nil {
		if err := messages.Complete(ctx, m.ID); err != nil {
			return fmt.Errorf("complete: %w", err)
		}
		logger.Info(ctx, "outbox delivered", "id", m.ID, "attempts", m.Attempts+1,
			"queued_ms", clock.Now(ctx).Sub(m.CreatedAt).Milliseconds())
		return nil
	}

	attempt := m.Attempts + 1
	if errors.Is(handleErr, errUnknownKind) || attempt >= MaxAttempts {
		if err := messages.Kill(ctx, m.ID, handleErr.Error()); err != nil {
			return fmt.Errorf("kill: %w", err)
		}
		return nil
	}
	logger.Warn(ctx, "outbox delivery failed", "id", m.ID, "attempt", attempt, "error", handleErr)
	if err := messages.Retry(ctx, m.ID, clock.Now(ctx).Add(backoff(attempt)), handleErr.Error()); err != nil {
		return fmt.Errorf("retry: %w", err)
	}
	return nil
}

func backoff(attempt int32) time.Duration {
	return min(baseBackoff<<attempt, maxBackoff)
}
