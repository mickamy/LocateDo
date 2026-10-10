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
	RecordCompletion(ctx context.Context, id, completerID uuid.UUID, completedAt time.Time) error
	// ReopenCompletions marks the todo's completions not reopened yet as
	// reopened at the given time.
	ReopenCompletions(ctx context.Context, id uuid.UUID, at time.Time) error
	// ClaimCompletionNotice marks the creator's to-dos in the household that
	// the completer checked off and nobody was told about, so each is
	// announced once, and returns them.
	ClaimCompletionNotice(
		ctx context.Context, householdID, creatorID, completerID uuid.UUID, at time.Time,
	) ([]model.CompletedTodo, error)
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
		Trigger:     model.Trigger{Event: model.PlaceEvent(row.NotifyOn)},
		AssigneeID:  row.AssigneeID,
		CreatorID:   row.CreatorID,
		CompletedAt: row.CompletedAt,
		UpdatedAt:   row.UpdatedAt,
		Version:     row.Version,
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
		NotifyOn:    string(t.Trigger.Event),
		AssigneeID:  t.AssigneeID,
		CreatorID:   t.CreatorID,
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

func (r todo) RecordCompletion(ctx context.Context, id, completerID uuid.UUID, completedAt time.Time) error {
	if err := r.q.InsertCompletion(ctx, queries.InsertCompletionParams{
		TodoID:      id,
		CompleterID: &completerID,
		CompletedAt: completedAt,
	}); err != nil {
		return fmt.Errorf("record completion: %w", err)
	}
	return nil
}

func (r todo) ReopenCompletions(ctx context.Context, id uuid.UUID, at time.Time) error {
	if err := r.q.ReopenCompletions(ctx, queries.ReopenCompletionsParams{TodoID: id, ReopenedAt: &at}); err != nil {
		return fmt.Errorf("reopen completions: %w", err)
	}
	return nil
}

func (r todo) ClaimCompletionNotice(
	ctx context.Context,
	householdID, creatorID, completerID uuid.UUID,
	at time.Time,
) ([]model.CompletedTodo, error) {
	rows, err := r.q.ClaimCompletionNotice(ctx, queries.ClaimCompletionNoticeParams{
		NotifiedAt:  &at,
		HouseholdID: householdID,
		CreatorID:   &creatorID,
		CompleterID: &completerID,
	})
	if err != nil {
		return nil, fmt.Errorf("claim completion notice: %w", err)
	}
	todos := make([]model.CompletedTodo, 0, len(rows))
	for _, row := range rows {
		todos = append(todos, model.CompletedTodo{Title: row.Title, CompletedAt: row.CompletedAt})
	}
	return todos, nil
}

func (r todo) Delete(ctx context.Context, id, householdID uuid.UUID) error {
	if err := r.q.DeleteTodo(ctx, queries.DeleteTodoParams{ID: id, HouseholdID: householdID}); err != nil {
		return fmt.Errorf("delete todo: %w", err)
	}
	return nil
}
