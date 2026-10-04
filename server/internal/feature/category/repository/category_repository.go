package repository

import (
	"context"
	"fmt"
	"uuid"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/category/model"
	"github.com/mickamy/LocateDo/internal/feature/category/queries"
	"github.com/mickamy/LocateDo/internal/infra/storage/db"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
)

type Category interface {
	// Upsert fails with a conflict when the id belongs to another household or
	// the household already has a category with the same builtin key.
	Upsert(ctx context.Context, c model.Category) error
	// Delete succeeds even when nothing matches.
	Delete(ctx context.Context, id, householdID uuid.UUID) error
	Bind(tx tx.Tx) Category
}

type category struct {
	q *queries.Queries
}

var _ Category = category{}

func NewCategory(reader db.Reader) Category {
	return category{q: queries.New(reader)}
}

func (r category) Bind(tx tx.Tx) Category {
	return category{q: queries.New(tx.DBTX())}
}

func (r category) Upsert(ctx context.Context, c model.Category) error {
	err := r.q.UpsertCategory(ctx, queries.UpsertCategoryParams{
		ID:          c.ID,
		HouseholdID: c.HouseholdID,
		BuiltinKey:  c.BuiltinKey,
		Name:        c.Name,
		Icon:        c.Icon,
		Color:       c.Color,
		SortOrder:   c.SortOrder,
	})
	switch {
	case db.IsUniqueViolation(err):
		return aerrors.Conflict("category")
	case db.IsForeignKeyViolation(err):
		return aerrors.InvalidArgument("category refers to an unknown row")
	case err != nil:
		return fmt.Errorf("upsert category: %w", err)
	}
	return nil
}

func (r category) Delete(ctx context.Context, id, householdID uuid.UUID) error {
	if err := r.q.DeleteCategory(ctx, queries.DeleteCategoryParams{ID: id, HouseholdID: householdID}); err != nil {
		return fmt.Errorf("delete category: %w", err)
	}
	return nil
}
