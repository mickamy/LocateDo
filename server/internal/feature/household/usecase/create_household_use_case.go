package usecase

import (
	"context"
	"errors"
	"fmt"
	"uuid"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/household/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
)

type CreateHouseholdInput struct {
	UserID      uuid.UUID
	HouseholdID uuid.UUID
	Contents    model.Contents
}

type CreateHouseholdOutput struct {
	Household model.Household
}

// CreateHousehold imports what a device built while signed out. A retry for a
// household the caller already owns returns it unchanged, so a device that
// lost the first response can safely send again.
type CreateHousehold struct {
	_           di.Infra              `di:"embed"`
	transactor  tx.Transactor         `di:""`
	households  repository.Household  `di:""`
	memberships repository.Membership `di:""`
}

func (uc CreateHousehold) Do(ctx context.Context, in CreateHouseholdInput) (CreateHouseholdOutput, error) {
	var h model.Household
	if err := uc.transactor.WithTx(ctx, func(tx tx.Tx) error {
		households := uc.households.Bind(tx)
		memberships := uc.memberships.Bind(tx)

		current, err := memberships.FindByUser(ctx, in.UserID)
		switch {
		case err == nil && current.HouseholdID == in.HouseholdID && current.Role == model.RoleOwner:
			if h, err = households.Find(ctx, in.HouseholdID); err != nil {
				return fmt.Errorf("find household: %w", err)
			}
			return nil
		case err == nil:
			return aerrors.Conflict("user already belongs to a household")
		case !errors.Is(err, aerrors.ErrNotFound):
			return fmt.Errorf("find membership: %w", err)
		}

		h, err = households.Create(ctx, in.HouseholdID, in.UserID)
		if err != nil {
			return fmt.Errorf("create household: %w", err)
		}
		if err := memberships.Create(ctx, model.Membership{
			HouseholdID: h.ID,
			UserID:      in.UserID,
			Role:        model.RoleOwner,
		}); err != nil {
			return fmt.Errorf("create membership: %w", err)
		}
		if err := households.Import(ctx, h.ID, in.Contents); err != nil {
			return fmt.Errorf("import contents: %w", err)
		}
		return nil
	}); err != nil {
		return CreateHouseholdOutput{}, fmt.Errorf("create household: %w", err)
	}
	return CreateHouseholdOutput{Household: h}, nil
}
