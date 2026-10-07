package repository

import (
	"context"
	"errors"
	"fmt"
	"uuid"

	"github.com/jackc/pgx/v5"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/household/queries"
	"github.com/mickamy/LocateDo/internal/infra/storage/db"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
)

type Household interface {
	Create(ctx context.Context, id, ownerID uuid.UUID) (model.Household, error)
	Find(ctx context.Context, id uuid.UUID) (model.Household, error)
	// FindForUpdate locks the household row until the transaction ends, so a
	// plan limit can be checked against a count that no other member changes.
	FindForUpdate(ctx context.Context, id uuid.UUID) (model.Household, error)
	FindByOwner(ctx context.Context, ownerID uuid.UUID) (model.Household, error)
	// AdvanceAllVersions moves every household's version and swept_version
	// forward by step and returns the households it touched.
	AdvanceAllVersions(ctx context.Context, step int64) ([]uuid.UUID, error)
	Delete(ctx context.Context, id uuid.UUID) error
	// MoveContents moves places, todos, and custom categories from one
	// household to another; places in a built-in category switch to the
	// destination's category with the same key, or become uncategorized. Call
	// it on a bound repository: it defers foreign-key checks to commit.
	MoveContents(ctx context.Context, from, to uuid.UUID) error
	// Import writes categories, places, and todos into the household, with the
	// user as the creator of every todo and the completer of completed ones. A
	// place or todo referring to a row that is neither imported nor already
	// there is an invalid argument.
	Import(ctx context.Context, householdID, userID uuid.UUID, c model.Contents) error
	Bind(tx tx.Tx) Household
}

type household struct {
	q *queries.Queries
}

var _ Household = household{}

func NewHousehold(reader db.Reader) Household {
	return household{q: queries.New(reader)}
}

func (r household) Bind(tx tx.Tx) Household {
	return household{q: queries.New(tx.DBTX())}
}

func (r household) Create(ctx context.Context, id, ownerID uuid.UUID) (model.Household, error) {
	row, err := r.q.CreateHousehold(ctx, queries.CreateHouseholdParams{ID: id, OwnerID: ownerID})
	if db.IsUniqueViolation(err) {
		return model.Household{}, aerrors.Conflict("household")
	}
	if err != nil {
		return model.Household{}, fmt.Errorf("create household: %w", err)
	}
	return model.Household{
		ID:           row.ID,
		OwnerID:      row.OwnerID,
		Plan:         model.Plan(row.Plan),
		Version:      row.Version,
		SweptVersion: row.SweptVersion,
		CreatedAt:    row.CreatedAt,
	}, nil
}

func (r household) Find(ctx context.Context, id uuid.UUID) (model.Household, error) {
	row, err := r.q.GetHousehold(ctx, id)
	if errors.Is(err, pgx.ErrNoRows) {
		return model.Household{}, aerrors.NotFound("household")
	}
	if err != nil {
		return model.Household{}, fmt.Errorf("get household: %w", err)
	}
	return model.Household{
		ID:           row.ID,
		OwnerID:      row.OwnerID,
		Plan:         model.Plan(row.Plan),
		Version:      row.Version,
		SweptVersion: row.SweptVersion,
		CreatedAt:    row.CreatedAt,
	}, nil
}

func (r household) FindForUpdate(ctx context.Context, id uuid.UUID) (model.Household, error) {
	row, err := r.q.GetHouseholdForUpdate(ctx, id)
	if errors.Is(err, pgx.ErrNoRows) {
		return model.Household{}, aerrors.NotFound("household")
	}
	if err != nil {
		return model.Household{}, fmt.Errorf("get household for update: %w", err)
	}
	return model.Household{
		ID:           row.ID,
		OwnerID:      row.OwnerID,
		Plan:         model.Plan(row.Plan),
		Version:      row.Version,
		SweptVersion: row.SweptVersion,
		CreatedAt:    row.CreatedAt,
	}, nil
}

func (r household) FindByOwner(ctx context.Context, ownerID uuid.UUID) (model.Household, error) {
	row, err := r.q.GetHouseholdByOwner(ctx, ownerID)
	if errors.Is(err, pgx.ErrNoRows) {
		return model.Household{}, aerrors.NotFound("household")
	}
	if err != nil {
		return model.Household{}, fmt.Errorf("get household by owner: %w", err)
	}
	return model.Household{
		ID:           row.ID,
		OwnerID:      row.OwnerID,
		Plan:         model.Plan(row.Plan),
		Version:      row.Version,
		SweptVersion: row.SweptVersion,
		CreatedAt:    row.CreatedAt,
	}, nil
}

func (r household) AdvanceAllVersions(ctx context.Context, step int64) ([]uuid.UUID, error) {
	ids, err := r.q.AdvanceAllHouseholdVersions(ctx, step)
	if err != nil {
		return nil, fmt.Errorf("advance household versions: %w", err)
	}
	return ids, nil
}

func (r household) Delete(ctx context.Context, id uuid.UUID) error {
	n, err := r.q.DeleteHousehold(ctx, id)
	if err != nil {
		return fmt.Errorf("delete household: %w", err)
	}
	if n == 0 {
		return aerrors.NotFound("household")
	}
	return nil
}

func (r household) MoveContents(ctx context.Context, from, to uuid.UUID) error {
	if err := r.q.DeferConstraints(ctx); err != nil {
		return fmt.Errorf("defer constraints: %w", err)
	}
	if err := r.q.RemapBuiltinCategories(ctx, queries.RemapBuiltinCategoriesParams{
		FromHouseholdID: from,
		ToHouseholdID:   to,
	}); err != nil {
		return fmt.Errorf("remap built-in categories: %w", err)
	}
	if err := r.q.MoveCustomCategories(ctx, queries.MoveCustomCategoriesParams{
		FromHouseholdID: from,
		ToHouseholdID:   to,
	}); err != nil {
		return fmt.Errorf("move custom categories: %w", err)
	}
	if err := r.q.MovePlaces(ctx, queries.MovePlacesParams{
		FromHouseholdID: from,
		ToHouseholdID:   to,
	}); err != nil {
		return fmt.Errorf("move places: %w", err)
	}
	if err := r.q.MoveTodos(ctx, queries.MoveTodosParams{
		FromHouseholdID: from,
		ToHouseholdID:   to,
	}); err != nil {
		return fmt.Errorf("move todos: %w", err)
	}
	return nil
}

func (r household) Import(ctx context.Context, householdID, userID uuid.UUID, c model.Contents) error {
	for _, cat := range c.Categories {
		err := r.q.ImportCategory(ctx, queries.ImportCategoryParams{
			ID:          cat.ID,
			HouseholdID: householdID,
			BuiltinKey:  cat.BuiltinKey,
			Name:        cat.Name,
			Icon:        cat.Icon,
			Color:       cat.Color,
			SortOrder:   cat.SortOrder,
		})
		if err != nil {
			return importError("category", cat.ID, err)
		}
	}
	for _, p := range c.Places {
		err := r.q.ImportPlace(ctx, queries.ImportPlaceParams{
			ID:          p.ID,
			HouseholdID: householdID,
			Name:        p.Name,
			Lat:         p.Lat,
			Lng:         p.Lng,
			RadiusM:     p.RadiusM,
			CategoryID:  p.CategoryID,
			SortOrder:   p.SortOrder,
		})
		if err != nil {
			return importError("place", p.ID, err)
		}
	}
	for _, t := range c.Todos {
		err := r.q.ImportTodo(ctx, queries.ImportTodoParams{
			ID:          t.Todo.ID,
			HouseholdID: householdID,
			PlaceID:     t.Todo.PlaceID,
			Title:       t.Todo.Title,
			AssigneeID:  t.Todo.AssigneeID,
			CreatorID:   &userID,
			CompletedAt: t.CompletedAt,
		})
		if err != nil {
			return importError("todo", t.Todo.ID, err)
		}
		if t.CompletedAt == nil {
			continue
		}
		if err := r.q.ImportCompletion(ctx, queries.ImportCompletionParams{
			TodoID:      t.Todo.ID,
			CompleterID: &userID,
			CompletedAt: *t.CompletedAt,
		}); err != nil {
			return importError("todo completion", t.Todo.ID, err)
		}
	}
	return nil
}

func importError(kind string, id uuid.UUID, err error) error {
	switch {
	case db.IsForeignKeyViolation(err):
		return aerrors.InvalidArgument(fmt.Sprintf("%s %s refers to an unknown row", kind, id))
	case db.IsUniqueViolation(err):
		return aerrors.Conflict(fmt.Sprintf("%s %s", kind, id))
	default:
		return fmt.Errorf("import %s %s: %w", kind, id, err)
	}
}
