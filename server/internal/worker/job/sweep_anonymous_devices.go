package job

import (
	"context"
	"fmt"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/feature/device/model"
	"github.com/mickamy/LocateDo/internal/feature/device/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/internal/lib/logger"
)

type SweepAnonymousDevices struct {
	_          di.Infra          `di:"embed"`
	transactor tx.Transactor     `di:""`
	devices    repository.Device `di:""`
}

func (j SweepAnonymousDevices) Run(ctx context.Context) error {
	before := clock.Now(ctx).Add(-model.AnonymousRetention)
	var swept int
	if err := j.transactor.WithTx(ctx, func(tx tx.Tx) error {
		var err error
		swept, err = j.devices.Bind(tx).DeleteAnonymousUnseenSince(ctx, before)
		if err != nil {
			return fmt.Errorf("delete: %w", err)
		}
		return nil
	}); err != nil {
		return fmt.Errorf("sweep anonymous devices: %w", err)
	}
	logger.Info(ctx, "swept anonymous devices", "count", swept)
	return nil
}
