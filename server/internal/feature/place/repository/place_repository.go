package repository

import (
	"context"
	"fmt"
	"uuid"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/place/model"
	"github.com/mickamy/LocateDo/internal/feature/place/queries"
	"github.com/mickamy/LocateDo/internal/infra/storage/db"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
)

type Place interface {
	Exists(ctx context.Context, id, householdID uuid.UUID) (bool, error)
	Count(ctx context.Context, householdID uuid.UUID) (int, error)
	// Upsert fails with a conflict when the id belongs to another household.
	Upsert(ctx context.Context, p model.Place) error
	// Delete succeeds even when nothing matches.
	Delete(ctx context.Context, id, householdID uuid.UUID) error
	Bind(tx tx.Tx) Place
}

type place struct {
	q *queries.Queries
}

var _ Place = place{}

func NewPlace(reader db.Reader) Place {
	return place{q: queries.New(reader)}
}

func (r place) Bind(tx tx.Tx) Place {
	return place{q: queries.New(tx.DBTX())}
}

func (r place) Exists(ctx context.Context, id, householdID uuid.UUID) (bool, error) {
	exists, err := r.q.PlaceExists(ctx, queries.PlaceExistsParams{ID: id, HouseholdID: householdID})
	if err != nil {
		return false, fmt.Errorf("place exists: %w", err)
	}
	return exists, nil
}

func (r place) Count(ctx context.Context, householdID uuid.UUID) (int, error) {
	n, err := r.q.CountPlaces(ctx, householdID)
	if err != nil {
		return 0, fmt.Errorf("count places: %w", err)
	}
	return int(n), nil
}

func (r place) Upsert(ctx context.Context, p model.Place) error {
	err := r.q.UpsertPlace(ctx, queries.UpsertPlaceParams{
		ID:          p.ID,
		HouseholdID: p.HouseholdID,
		Name:        p.Name,
		Lat:         p.Lat,
		Lng:         p.Lng,
		RadiusM:     p.RadiusM,
		CategoryID:  p.CategoryID,
		SortOrder:   p.SortOrder,
	})
	switch {
	case db.IsUniqueViolation(err):
		return aerrors.Conflict("place")
	case db.IsForeignKeyViolation(err):
		return aerrors.InvalidArgument("place refers to an unknown row")
	case err != nil:
		return fmt.Errorf("upsert place: %w", err)
	}
	return nil
}

func (r place) Delete(ctx context.Context, id, householdID uuid.UUID) error {
	if err := r.q.DeletePlace(ctx, queries.DeletePlaceParams{ID: id, HouseholdID: householdID}); err != nil {
		return fmt.Errorf("delete place: %w", err)
	}
	return nil
}
