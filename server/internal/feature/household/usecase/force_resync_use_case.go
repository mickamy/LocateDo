package usecase

import (
	"context"
	"fmt"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/feature/household/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/internal/outbox"
)

// resyncStep is far beyond any version a household reaches between backups.
const resyncStep = 1_000_000_000

// ForceResync makes every device pull its household from scratch, as needed
// after restoring an older backup: devices then hold cursors past the restored
// versions and would otherwise miss changes or keep rows that are gone.
type ForceResync struct {
	_          di.Infra             `di:"embed"`
	transactor tx.Transactor        `di:""`
	households repository.Household `di:""`
	messages   outbox.Repository    `di:""`
}

func (uc ForceResync) Do(ctx context.Context) (int, error) {
	var n int
	if err := uc.transactor.WithTx(ctx, func(tx tx.Tx) error {
		ids, err := uc.households.Bind(tx).AdvanceAllVersions(ctx, resyncStep)
		if err != nil {
			return fmt.Errorf("advance versions: %w", err)
		}
		messages := uc.messages.Bind(tx)
		for _, id := range ids {
			if err := messages.Enqueue(ctx, outbox.PushHousehold(id, clock.Now(ctx))); err != nil {
				return fmt.Errorf("enqueue push: %w", err)
			}
		}
		n = len(ids)
		return nil
	}); err != nil {
		return 0, fmt.Errorf("force resync: %w", err)
	}
	return n, nil
}
