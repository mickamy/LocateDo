package usecase

import (
	"context"
	"fmt"
	"uuid"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/feature/category/repository"
	hrepository "github.com/mickamy/LocateDo/internal/feature/household/repository"
	husecase "github.com/mickamy/LocateDo/internal/feature/household/usecase"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
)

type DeleteCategoryInput struct {
	UserID     uuid.UUID
	CategoryID uuid.UUID
}

// DeleteCategory removes the category from the caller's household; its places
// become uncategorized. It succeeds when the category is already gone.
type DeleteCategory struct {
	_           di.Infra               `di:"embed"`
	transactor  tx.Transactor          `di:""`
	memberships hrepository.Membership `di:""`
	categories  repository.Category    `di:""`
}

func (uc DeleteCategory) Do(ctx context.Context, in DeleteCategoryInput) error {
	if err := uc.transactor.WithTx(ctx, func(tx tx.Tx) error {
		householdID, err := husecase.CallerHousehold(ctx, uc.memberships.Bind(tx), in.UserID)
		if err != nil {
			return fmt.Errorf("caller household: %w", err)
		}
		if err := uc.categories.Bind(tx).Delete(ctx, in.CategoryID, householdID); err != nil {
			return fmt.Errorf("delete category: %w", err)
		}
		return nil
	}); err != nil {
		return fmt.Errorf("delete category: %w", err)
	}
	return nil
}
