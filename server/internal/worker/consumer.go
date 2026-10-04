package worker

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"time"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/internal/outbox"
)

const (
	PollInterval = time.Second
	MaxAttempts  = 20
	baseBackoff  = 10 * time.Second
	maxBackoff   = time.Hour
)

type Handler interface {
	Handle(ctx context.Context, m outbox.Message) error
}

type HandlerFunc func(ctx context.Context, m outbox.Message) error

func (f HandlerFunc) Handle(ctx context.Context, m outbox.Message) error {
	return f(ctx, m)
}

type Handlers map[outbox.Kind]Handler

func NewHandlers() Handlers {
	return Handlers{}
}

// Consumer delivers outbox messages one at a time, holding each row's lock
// for the whole delivery so a crash simply returns the message to the queue.
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
			slog.ErrorContext(ctx, "outbox step failed", "error", err)
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
	var delivered bool
	if err := c.transactor.WithTx(ctx, func(tx tx.Tx) error {
		messages := c.messages.Bind(tx)
		m, err := messages.Claim(ctx, clock.Now(ctx))
		if errors.Is(err, aerrors.ErrNotFound) {
			return nil
		}
		if err != nil {
			return fmt.Errorf("claim: %w", err)
		}
		delivered = true
		return c.deliver(ctx, messages, m)
	}); err != nil {
		return delivered, fmt.Errorf("step: %w", err)
	}
	return delivered, nil
}

func (c Consumer) deliver(ctx context.Context, messages outbox.Repository, m outbox.Message) error {
	handler, ok := c.handlers[m.Kind]
	if !ok {
		if err := messages.Kill(ctx, m.ID, "no handler for kind "+string(m.Kind)); err != nil {
			return fmt.Errorf("kill: %w", err)
		}
		return nil
	}
	err := handler.Handle(ctx, m)
	if err == nil {
		if err := messages.Complete(ctx, m.ID); err != nil {
			return fmt.Errorf("complete: %w", err)
		}
		return nil
	}

	attempt := m.Attempts + 1
	slog.WarnContext(ctx, "outbox delivery failed", "kind", m.Kind, "id", m.ID, "attempt", attempt, "error", err)
	if attempt >= MaxAttempts {
		if err := messages.Kill(ctx, m.ID, err.Error()); err != nil {
			return fmt.Errorf("kill: %w", err)
		}
		return nil
	}
	if err := messages.Retry(ctx, m.ID, clock.Now(ctx).Add(backoff(attempt)), err.Error()); err != nil {
		return fmt.Errorf("retry: %w", err)
	}
	return nil
}

func backoff(attempt int32) time.Duration {
	return min(baseBackoff<<attempt, maxBackoff)
}
