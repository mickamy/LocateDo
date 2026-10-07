package job

import (
	"context"
	"fmt"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/household/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/internal/lib/logger"
	"github.com/mickamy/LocateDo/internal/outbox"
)

// ReconcileEntitlements queues a plan re-check for every Pro owner, so a
// subscription whose webhooks never arrived still lapses.
type ReconcileEntitlements struct {
	_          di.Infra          `di:"embed"`
	transactor tx.Transactor     `di:""`
	plans      repository.Plan   `di:""`
	messages   outbox.Repository `di:""`
}

func (j ReconcileEntitlements) Run(ctx context.Context) error {
	now := clock.Now(ctx)
	var queued int
	if err := j.transactor.WithTx(ctx, func(tx tx.Tx) error {
		owners, err := j.plans.Bind(tx).Owners(ctx, model.PlanPro)
		if err != nil {
			return fmt.Errorf("list pro owners: %w", err)
		}
		messages := j.messages.Bind(tx)
		for _, owner := range owners {
			if err := messages.Enqueue(ctx, outbox.SyncEntitlement(owner, now)); err != nil {
				return fmt.Errorf("enqueue sync entitlement: %w", err)
			}
		}
		queued = len(owners)
		return nil
	}); err != nil {
		return fmt.Errorf("reconcile entitlements: %w", err)
	}
	logger.Info(ctx, "queued entitlement checks", "count", queued)
	return nil
}
