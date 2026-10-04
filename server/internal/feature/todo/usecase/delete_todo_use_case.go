package usecase

import (
	"context"
	"fmt"
	"uuid"

	"github.com/mickamy/LocateDo/internal/di"
	hrepository "github.com/mickamy/LocateDo/internal/feature/household/repository"
	husecase "github.com/mickamy/LocateDo/internal/feature/household/usecase"
	"github.com/mickamy/LocateDo/internal/feature/todo/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
)

type DeleteTodoInput struct {
	UserID uuid.UUID
	TodoID uuid.UUID
}

// DeleteTodo removes the todo from the caller's household. It succeeds when
// the todo is already gone, so a retried request is harmless.
type DeleteTodo struct {
	_           di.Infra               `di:"embed"`
	transactor  tx.Transactor          `di:""`
	memberships hrepository.Membership `di:""`
	todos       repository.Todo        `di:""`
}

func (uc DeleteTodo) Do(ctx context.Context, in DeleteTodoInput) error {
	if err := uc.transactor.WithTx(ctx, func(tx tx.Tx) error {
		householdID, err := husecase.CallerHousehold(ctx, uc.memberships.Bind(tx), in.UserID)
		if err != nil {
			return fmt.Errorf("caller household: %w", err)
		}
		if err := uc.todos.Bind(tx).Delete(ctx, in.TodoID, householdID); err != nil {
			return fmt.Errorf("delete todo: %w", err)
		}
		return nil
	}); err != nil {
		return fmt.Errorf("delete todo: %w", err)
	}
	return nil
}
