package usecase

import (
	"context"
	"fmt"
	"uuid"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/feature/todo/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
)

type DeleteTodoInput struct {
	HouseholdID uuid.UUID
	TodoID      uuid.UUID
}

// DeleteTodo removes the todo from the caller's household. It succeeds when
// the todo is already gone, so a retried request is harmless.
type DeleteTodo struct {
	_          di.Infra        `di:"embed"`
	transactor tx.Transactor   `di:""`
	todos      repository.Todo `di:""`
}

func (uc DeleteTodo) Do(ctx context.Context, in DeleteTodoInput) error {
	if err := uc.transactor.WithTx(ctx, func(tx tx.Tx) error {
		if err := uc.todos.Bind(tx).Delete(ctx, in.TodoID, in.HouseholdID); err != nil {
			return fmt.Errorf("delete todo: %w", err)
		}
		return nil
	}); err != nil {
		return fmt.Errorf("delete todo: %w", err)
	}
	return nil
}
