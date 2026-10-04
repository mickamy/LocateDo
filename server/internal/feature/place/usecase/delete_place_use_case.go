package usecase

import (
	"context"
	"fmt"
	"uuid"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/feature/place/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
)

type DeletePlaceInput struct {
	HouseholdID uuid.UUID
	PlaceID     uuid.UUID
}

// DeletePlace removes the place from the caller's household. It succeeds
// when the place is already gone, so a retried request is harmless.
type DeletePlace struct {
	_          di.Infra         `di:"embed"`
	transactor tx.Transactor    `di:""`
	places     repository.Place `di:""`
}

func (uc DeletePlace) Do(ctx context.Context, in DeletePlaceInput) error {
	if err := uc.transactor.WithTx(ctx, func(tx tx.Tx) error {
		if err := uc.places.Bind(tx).Delete(ctx, in.PlaceID, in.HouseholdID); err != nil {
			return fmt.Errorf("delete place: %w", err)
		}
		return nil
	}); err != nil {
		return fmt.Errorf("delete place: %w", err)
	}
	return nil
}
