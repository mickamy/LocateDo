package usecase

import (
	"context"
	"errors"
	"fmt"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/account/repository"
	dmodel "github.com/mickamy/LocateDo/internal/feature/device/model"
	drepository "github.com/mickamy/LocateDo/internal/feature/device/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/internal/lib/token"
)

type SignOutInput struct {
	RefreshToken string
	Device       *SignOutDevice
}

type SignOutDevice struct {
	Platform  dmodel.Platform
	PushToken string
}

type SignOut struct {
	_          di.Infra                `di:"embed"`
	transactor tx.Transactor           `di:""`
	tokens     repository.RefreshToken `di:""`
	devices    drepository.Device      `di:""`
}

func (uc SignOut) Do(ctx context.Context, in SignOutInput) error {
	hash := token.HashOpaque(in.RefreshToken)

	if err := uc.transactor.WithTx(ctx, func(tx tx.Tx) error {
		tokens := uc.tokens.Bind(tx)

		current, err := tokens.FindByHash(ctx, hash)
		if errors.Is(err, aerrors.ErrNotFound) {
			return nil
		}
		if err != nil {
			return fmt.Errorf("find refresh token: %w", err)
		}
		if err := tokens.RevokeFamily(ctx, current.FamilyID); err != nil {
			return fmt.Errorf("revoke refresh token family: %w", err)
		}

		if in.Device == nil {
			return nil
		}
		if err := uc.devices.Bind(tx).ReleaseOwnedByToken(
			ctx, current.UserID, in.Device.Platform, in.Device.PushToken,
		); err != nil {
			return fmt.Errorf("release device: %w", err)
		}
		return nil
	}); err != nil {
		return fmt.Errorf("sign out: %w", err)
	}
	return nil
}
