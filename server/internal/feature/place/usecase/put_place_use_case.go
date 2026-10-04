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

// PutPlaceInput carries the caller's household and the place to write; the
// two must agree.
type PutPlaceInput struct {
	HouseholdID uuid.UUID
	Place       model.Place
}

// PutPlace creates or overwrites a place. A free household keeps every place
// it already has but cannot add one beyond the limit; the limit is checked
// after the write so the transaction, not a pre-check, enforces it.
type PutPlace struct {
	_          di.Infra              `di:"embed"`
	transactor tx.Transactor         `di:""`
	households hrepository.Household `di:""`
	places     repository.Place      `di:""`
}

func (uc PutPlace) Do(ctx context.Context, in PutPlaceInput) error {
	if in.Place.HouseholdID != in.HouseholdID {
		return aerrors.PermissionDenied("not a member of this household")
	}
	if err := uc.transactor.WithTx(ctx, func(tx tx.Tx) error {
		h, err := uc.households.Bind(tx).FindForUpdate(ctx, in.HouseholdID)
		if err != nil {
			return fmt.Errorf("lock household: %w", err)
		}
		places := uc.places.Bind(tx)
		exists, err := places.Exists(ctx, in.Place.ID, in.HouseholdID)
		if err != nil {
			return fmt.Errorf("check place: %w", err)
		}
		if err := places.Upsert(ctx, in.Place); err != nil {
			return fmt.Errorf("upsert place: %w", err)
		}
		if exists {
			return nil
		}
		n, err := places.Count(ctx, in.HouseholdID)
		if err != nil {
			return fmt.Errorf("count places: %w", err)
		}
		if !h.Plan.AllowsPlaces(n) {
			return aerrors.Precondition(fmt.Sprintf("the free plan allows %d places", hmodel.MaxFreePlaces))
		}
		return nil
	}); err != nil {
		return fmt.Errorf("put place: %w", err)
	}
	return nil
}
