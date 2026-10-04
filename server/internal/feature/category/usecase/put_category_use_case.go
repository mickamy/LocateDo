package usecase

import (
	"context"
	"fmt"
	"uuid"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/category/model"
	"github.com/mickamy/LocateDo/internal/feature/category/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
)

// PutCategoryInput carries the caller's household and the category to write;
// the two must agree.
type PutCategoryInput struct {
	HouseholdID uuid.UUID
	Category    model.Category
}

type PutCategory struct {
	_          di.Infra            `di:"embed"`
	transactor tx.Transactor       `di:""`
	categories repository.Category `di:""`
}

func (uc PutCategory) Do(ctx context.Context, in PutCategoryInput) error {
	if in.Category.HouseholdID != in.HouseholdID {
		return aerrors.PermissionDenied("not a member of this household")
	}
	if err := uc.transactor.WithTx(ctx, func(tx tx.Tx) error {
		if err := uc.categories.Bind(tx).Upsert(ctx, in.Category); err != nil {
			return fmt.Errorf("upsert category: %w", err)
		}
		return nil
	}); err != nil {
		return fmt.Errorf("put category: %w", err)
	}
	return nil
}
