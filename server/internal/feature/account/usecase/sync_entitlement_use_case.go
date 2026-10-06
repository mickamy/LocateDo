package usecase

import (
	"context"
	"fmt"
	"uuid"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/internal/outbox"
)

type SyncEntitlementInput struct {
	UserID uuid.UUID
}

type SyncEntitlement struct {
	_          di.Infra          `di:"embed"`
	transactor tx.Transactor     `di:""`
	messages   outbox.Repository `di:""`
}

func (uc SyncEntitlement) Do(ctx context.Context, in SyncEntitlementInput) error {
	if err := uc.transactor.WithTx(ctx, func(tx tx.Tx) error {
		return uc.messages.Bind(tx).Enqueue(ctx, outbox.SyncEntitlement(in.UserID, clock.Now(ctx)))
	}); err != nil {
		return fmt.Errorf("enqueue sync entitlement: %w", err)
	}
	return nil
}
