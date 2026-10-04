package job

import (
	"context"
	"fmt"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/feature/sync/model"
	"github.com/mickamy/LocateDo/internal/feature/sync/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/internal/lib/logger"
)

type SweepTombstones struct {
	_          di.Infra              `di:"embed"`
	transactor tx.Transactor         `di:""`
	tombstones repository.Tombstones `di:""`
}

func (j SweepTombstones) Run(ctx context.Context) error {
	before := clock.Now(ctx).Add(-model.TombstoneRetention)
	var swept int
	if err := j.transactor.WithTx(ctx, func(tx tx.Tx) error {
		var err error
		swept, err = j.tombstones.Bind(tx).Sweep(ctx, before)
		if err != nil {
			return fmt.Errorf("sweep: %w", err)
		}
		return nil
	}); err != nil {
		return fmt.Errorf("sweep tombstones: %w", err)
	}
	logger.Info(ctx, "swept tombstones", "count", swept)
	return nil
}
