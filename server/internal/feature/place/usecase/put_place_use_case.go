package usecase

import (
	"context"
	"fmt"
	"uuid"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	hmodel "github.com/mickamy/LocateDo/internal/feature/household/model"
	hrepository "github.com/mickamy/LocateDo/internal/feature/household/repository"
	"github.com/mickamy/LocateDo/internal/feature/place/model"
	"github.com/mickamy/LocateDo/internal/feature/place/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
)

type PutPlaceInput struct {
	UserID uuid.UUID
	Place  model.Place
}

// PutPlace creates or overwrites a place. A free household keeps every place
// it already has but cannot add one beyond the limit.
type PutPlace struct {
	_           di.Infra               `di:"embed"`
	transactor  tx.Transactor          `di:""`
	households  hrepository.Household  `di:""`
	memberships hrepository.Membership `di:""`
	places      repository.Place       `di:""`
}

func (uc PutPlace) Do(ctx context.Context, in PutPlaceInput) error {
	if err := uc.transactor.WithTx(ctx, func(tx tx.Tx) error {
		householdID, err := callerHousehold(ctx, uc.memberships.Bind(tx), in.UserID)
		if err != nil {
			return err
		}
		if householdID != in.Place.HouseholdID {
			return aerrors.PermissionDenied("not a member of this household")
		}

		h, err := uc.households.Bind(tx).FindForUpdate(ctx, householdID)
		if err != nil {
			return fmt.Errorf("lock household: %w", err)
		}
		places := uc.places.Bind(tx)
		if h.Plan == hmodel.PlanFree {
			if err := requireRoomForPlace(ctx, places, in.Place); err != nil {
				return err
			}
		}
		if err := places.Upsert(ctx, in.Place); err != nil {
			return fmt.Errorf("upsert place: %w", err)
		}
		return nil
	}); err != nil {
		return fmt.Errorf("put place: %w", err)
	}
	return nil
}

func requireRoomForPlace(ctx context.Context, places repository.Place, p model.Place) error {
	exists, err := places.Exists(ctx, p.ID, p.HouseholdID)
	if err != nil {
		return fmt.Errorf("check place: %w", err)
	}
	if exists {
		return nil
	}
	n, err := places.Count(ctx, p.HouseholdID)
	if err != nil {
		return fmt.Errorf("count places: %w", err)
	}
	if n >= hmodel.MaxFreePlaces {
		return aerrors.Precondition(fmt.Sprintf("a free household holds up to %d places", hmodel.MaxFreePlaces))
	}
	return nil
}
