package job

import (
	"context"
	"fmt"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/feature/account/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/internal/lib/logger"
)

type SweepRefreshTokens struct {
	_             di.Infra                `di:"embed"`
	transactor    tx.Transactor           `di:""`
	refreshTokens repository.RefreshToken `di:""`
}

func (j SweepRefreshTokens) Run(ctx context.Context) error {
	var swept int
	if err := j.transactor.WithTx(ctx, func(tx tx.Tx) error {
		var err error
		swept, err = j.refreshTokens.Bind(tx).DeleteExpired(ctx, clock.Now(ctx))
		if err != nil {
			return fmt.Errorf("delete expired: %w", err)
		}
		return nil
	}); err != nil {
		return fmt.Errorf("sweep refresh tokens: %w", err)
	}
	logger.Info(ctx, "swept expired refresh tokens", "count", swept)
	return nil
}
