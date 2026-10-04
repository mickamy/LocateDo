package usecase

import (
	"context"
	"errors"
	"fmt"
	"time"
	"uuid"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	hmodel "github.com/mickamy/LocateDo/internal/feature/household/model"
	hrepository "github.com/mickamy/LocateDo/internal/feature/household/repository"
	husecase "github.com/mickamy/LocateDo/internal/feature/household/usecase"
	"github.com/mickamy/LocateDo/internal/feature/todo/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
)

type SetTodoCompletionInput struct {
	UserID      uuid.UUID
	TodoID      uuid.UUID
	CompletedAt *time.Time
}

// SetTodoCompletion completes a todo or reopens it. Reopening counts as
// adding an open todo for the free-tier limit. A missing todo is a no-op.
type SetTodoCompletion struct {
	_           di.Infra               `di:"embed"`
	transactor  tx.Transactor          `di:""`
	households  hrepository.Household  `di:""`
	memberships hrepository.Membership `di:""`
	todos       repository.Todo        `di:""`
}

func (uc SetTodoCompletion) Do(ctx context.Context, in SetTodoCompletionInput) error {
	if err := uc.transactor.WithTx(ctx, func(tx tx.Tx) error {
		householdID, err := husecase.CallerHousehold(ctx, uc.memberships.Bind(tx), in.UserID)
		if err != nil {
			return fmt.Errorf("caller household: %w", err)
		}
		h, err := uc.households.Bind(tx).FindForUpdate(ctx, householdID)
		if err != nil {
			return fmt.Errorf("lock household: %w", err)
		}

		todos := uc.todos.Bind(tx)
		current, err := todos.Find(ctx, in.TodoID, householdID)
		if errors.Is(err, aerrors.ErrNotFound) {
			return nil
		}
		if err != nil {
			return fmt.Errorf("find todo: %w", err)
		}
		if err := todos.SetCompletion(ctx, in.TodoID, householdID, in.CompletedAt); err != nil {
			return fmt.Errorf("set completion: %w", err)
		}
		reopening := in.CompletedAt == nil && current.CompletedAt != nil
		if reopening && h.Plan == hmodel.PlanFree {
			return requireOpenTodosWithinLimit(ctx, todos, householdID)
		}
		return nil
	}); err != nil {
		return fmt.Errorf("set todo completion: %w", err)
	}
	return nil
}
