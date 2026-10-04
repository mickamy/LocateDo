package repository

import (
	"context"
	"errors"
	"fmt"
	"time"
	"uuid"

	"github.com/jackc/pgx/v5"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/todo/model"
	"github.com/mickamy/LocateDo/internal/feature/todo/queries"
	"github.com/mickamy/LocateDo/internal/infra/storage/db"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
)

type Todo interface {
	Find(ctx context.Context, id, householdID uuid.UUID) (model.Todo, error)
	CountOpen(ctx context.Context, householdID uuid.UUID) (int, error)
	// Upsert writes everything but CompletedAt. It reports false, and writes
	// nothing, when the place is not in the household, and fails with a
	// conflict when the id belongs to another household.
	Upsert(ctx context.Context, t model.Todo) (bool, error)
	// SetCompletion and Delete succeed even when nothing matches.
	SetCompletion(ctx context.Context, id, householdID uuid.UUID, completedAt *time.Time) error
	Delete(ctx context.Context, id, householdID uuid.UUID) error
	Bind(tx tx.Tx) Todo
}

type todo struct {
	q *queries.Queries
}

var _ Todo = todo{}

func NewTodo(reader db.Reader) Todo {
	return todo{q: queries.New(reader)}
}

func (r todo) Bind(tx tx.Tx) Todo {
	return todo{q: queries.New(tx.DBTX())}
}

func (r todo) Find(ctx context.Context, id, householdID uuid.UUID) (model.Todo, error) {
	row, err := r.q.GetTodo(ctx, queries.GetTodoParams{ID: id, HouseholdID: householdID})
	if errors.Is(err, pgx.ErrNoRows) {
		return model.Todo{}, aerrors.NotFound("todo")
	}
	if err != nil {
		return model.Todo{}, fmt.Errorf("get todo: %w", err)
	}
	return model.Todo{
		ID:          row.ID,
		HouseholdID: row.HouseholdID,
		PlaceID:     row.PlaceID,
		Title:       row.Title,
		AssigneeID:  row.AssigneeID,
		CompletedAt: row.CompletedAt,
	}, nil
}

func (r todo) CountOpen(ctx context.Context, householdID uuid.UUID) (int, error) {
	n, err := r.q.CountOpenTodos(ctx, householdID)
	if err != nil {
		return 0, fmt.Errorf("count open todos: %w", err)
	}
	return int(n), nil
}

func (r todo) Upsert(ctx context.Context, t model.Todo) (bool, error) {
	n, err := r.q.UpsertTodo(ctx, queries.UpsertTodoParams{
		ID:          t.ID,
		HouseholdID: t.HouseholdID,
		PlaceID:     t.PlaceID,
		Title:       t.Title,
		AssigneeID:  t.AssigneeID,
	})
	switch {
	case db.IsUniqueViolation(err):
		return false, aerrors.Conflict("todo")
	case db.IsForeignKeyViolation(err):
		return false, aerrors.InvalidArgument("todo refers to an unknown row")
	case err != nil:
		return false, fmt.Errorf("upsert todo: %w", err)
	}
	return n > 0, nil
}

func (r todo) SetCompletion(ctx context.Context, id, householdID uuid.UUID, completedAt *time.Time) error {
	if err := r.q.SetTodoCompletion(ctx, queries.SetTodoCompletionParams{
		ID:          id,
		HouseholdID: householdID,
		CompletedAt: completedAt,
	}); err != nil {
		return fmt.Errorf("set todo completion: %w", err)
	}
	return nil
}

func (r todo) Delete(ctx context.Context, id, householdID uuid.UUID) error {
	if err := r.q.DeleteTodo(ctx, queries.DeleteTodoParams{ID: id, HouseholdID: householdID}); err != nil {
		return fmt.Errorf("delete todo: %w", err)
	}
	return nil
}
