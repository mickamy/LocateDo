package usecase

import (
	"context"
	"errors"
	"fmt"
	"uuid"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	hmodel "github.com/mickamy/LocateDo/internal/feature/household/model"
	hrepository "github.com/mickamy/LocateDo/internal/feature/household/repository"
	husecase "github.com/mickamy/LocateDo/internal/feature/household/usecase"
	"github.com/mickamy/LocateDo/internal/feature/todo/model"
	"github.com/mickamy/LocateDo/internal/feature/todo/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
)

type PutTodoInput struct {
	UserID uuid.UUID
	Todo   model.Todo
}

// PutTodo creates or overwrites a todo's content, never its completion. A
// free household keeps every todo it has but cannot open one beyond the
// limit; the limit is checked after the write so the transaction, not a
// pre-check, enforces it. A todo for a place that is gone is dropped and
// reported as success.
type PutTodo struct {
	_           di.Infra               `di:"embed"`
	transactor  tx.Transactor          `di:""`
	households  hrepository.Household  `di:""`
	memberships hrepository.Membership `di:""`
	todos       repository.Todo        `di:""`
}

func (uc PutTodo) Do(ctx context.Context, in PutTodoInput) error {
	if err := uc.transactor.WithTx(ctx, func(tx tx.Tx) error {
		householdID, err := husecase.CallerHousehold(ctx, uc.memberships.Bind(tx), in.UserID)
		if err != nil {
			return fmt.Errorf("caller household: %w", err)
		}
		if householdID != in.Todo.HouseholdID {
			return aerrors.PermissionDenied("not a member of this household")
		}

		h, err := uc.households.Bind(tx).FindForUpdate(ctx, householdID)
		if err != nil {
			return fmt.Errorf("lock household: %w", err)
		}
		todos := uc.todos.Bind(tx)
		_, err = todos.Find(ctx, in.Todo.ID, householdID)
		isNew := errors.Is(err, aerrors.ErrNotFound)
		if err != nil && !isNew {
			return fmt.Errorf("find todo: %w", err)
		}

		written, err := todos.Upsert(ctx, in.Todo)
		if err != nil {
			return fmt.Errorf("upsert todo: %w", err)
		}
		if !written || !isNew {
			return nil
		}
		n, err := todos.CountOpen(ctx, householdID)
		if err != nil {
			return fmt.Errorf("count open todos: %w", err)
		}
		if !h.Plan.AllowsOpenTodos(n) {
			return aerrors.Precondition(fmt.Sprintf("the free plan allows %d open todos", hmodel.MaxFreeOpenTodos))
		}
		return nil
	}); err != nil {
		return fmt.Errorf("put todo: %w", err)
	}
	return nil
}
