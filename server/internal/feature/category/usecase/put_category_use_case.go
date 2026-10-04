package usecase

import (
	"context"
	"fmt"
	"uuid"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/category/model"
	"github.com/mickamy/LocateDo/internal/feature/category/repository"
	hrepository "github.com/mickamy/LocateDo/internal/feature/household/repository"
	husecase "github.com/mickamy/LocateDo/internal/feature/household/usecase"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
)

type PutCategoryInput struct {
	UserID   uuid.UUID
	Category model.Category
}

type PutCategory struct {
	_           di.Infra               `di:"embed"`
	transactor  tx.Transactor          `di:""`
	memberships hrepository.Membership `di:""`
	categories  repository.Category    `di:""`
}

func (uc PutCategory) Do(ctx context.Context, in PutCategoryInput) error {
	if err := uc.transactor.WithTx(ctx, func(tx tx.Tx) error {
		householdID, err := husecase.CallerHousehold(ctx, uc.memberships.Bind(tx), in.UserID)
		if err != nil {
			return fmt.Errorf("caller household: %w", err)
		}
		if householdID != in.Category.HouseholdID {
			return aerrors.PermissionDenied("not a member of this household")
		}
		if err := uc.categories.Bind(tx).Upsert(ctx, in.Category); err != nil {
			return fmt.Errorf("upsert category: %w", err)
		}
		return nil
	}); err != nil {
		return fmt.Errorf("put category: %w", err)
	}
	return nil
}
