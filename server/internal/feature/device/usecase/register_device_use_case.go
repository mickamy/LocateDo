package usecase

import (
	"context"
	"fmt"
	"uuid"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/feature/device/model"
	"github.com/mickamy/LocateDo/internal/feature/device/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/internal/lib/clock"
)

type RegisterDeviceInput struct {
	UserID uuid.UUID
	Device model.Device
}

type RegisterDevice struct {
	_          di.Infra          `di:"embed"`
	transactor tx.Transactor     `di:""`
	devices    repository.Device `di:""`
}

func (uc RegisterDevice) Do(ctx context.Context, in RegisterDeviceInput) error {
	d := in.Device
	d.UserID = in.UserID
	d.LastSeenAt = clock.Now(ctx)
	if err := uc.transactor.WithTx(ctx, func(tx tx.Tx) error {
		if err := uc.devices.Bind(tx).Upsert(ctx, d); err != nil {
			return fmt.Errorf("upsert device: %w", err)
		}
		return nil
	}); err != nil {
		return fmt.Errorf("register device: %w", err)
	}
	return nil
}
