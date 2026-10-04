package job

import (
	"context"
	"fmt"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/internal/lib/logger"
	"github.com/mickamy/LocateDo/internal/outbox"
)

type SweepDeadMessages struct {
	_          di.Infra          `di:"embed"`
	transactor tx.Transactor     `di:""`
	messages   outbox.Repository `di:""`
}

func (j SweepDeadMessages) Run(ctx context.Context) error {
	before := clock.Now(ctx).Add(-outbox.DeadRetention)
	var swept int
	if err := j.transactor.WithTx(ctx, func(tx tx.Tx) error {
		var err error
		swept, err = j.messages.Bind(tx).SweepDead(ctx, before)
		if err != nil {
			return fmt.Errorf("sweep dead: %w", err)
		}
		return nil
	}); err != nil {
		return fmt.Errorf("sweep dead messages: %w", err)
	}
	logger.Info(ctx, "swept dead messages", "count", swept)
	return nil
}
