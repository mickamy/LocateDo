package repository

import (
	"context"
	"fmt"
	"uuid"

	cmodel "github.com/mickamy/LocateDo/internal/feature/category/model"
	hmodel "github.com/mickamy/LocateDo/internal/feature/household/model"
	pmodel "github.com/mickamy/LocateDo/internal/feature/place/model"
	"github.com/mickamy/LocateDo/internal/feature/sync/model"
	"github.com/mickamy/LocateDo/internal/feature/sync/queries"
	tmodel "github.com/mickamy/LocateDo/internal/feature/todo/model"
	"github.com/mickamy/LocateDo/internal/infra/storage/db"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
)

// Changes reads what changed in a household after a version, oldest first,
// at most limit rows per call.
type Changes interface {
	Memberships(ctx context.Context, householdID uuid.UUID, cursor int64, limit int32) ([]hmodel.Membership, error)
	Categories(ctx context.Context, householdID uuid.UUID, cursor int64, limit int32) ([]cmodel.Category, error)
	Places(ctx context.Context, householdID uuid.UUID, cursor int64, limit int32) ([]pmodel.Place, error)
	Todos(ctx context.Context, householdID uuid.UUID, cursor int64, limit int32) ([]tmodel.Todo, error)
	Deletions(ctx context.Context, householdID uuid.UUID, cursor int64, limit int32) ([]model.Deletion, error)
	Bind(tx tx.Tx) Changes
}

type changes struct {
	q *queries.Queries
}

var _ Changes = changes{}

func NewChanges(reader db.Reader) Changes {
	return changes{q: queries.New(reader)}
}

func (r changes) Bind(tx tx.Tx) Changes {
	return changes{q: queries.New(tx.DBTX())}
}

func (r changes) Memberships(
	ctx context.Context, householdID uuid.UUID, cursor int64, limit int32,
) ([]hmodel.Membership, error) {
	rows, err := r.q.ListMembershipChanges(ctx, queries.ListMembershipChangesParams{
		HouseholdID: householdID, Version: cursor, Limit: limit,
	})
	if err != nil {
		return nil, fmt.Errorf("list membership changes: %w", err)
	}
	out := make([]hmodel.Membership, len(rows))
	for i, row := range rows {
		out[i] = hmodel.Membership{
			HouseholdID: row.HouseholdID,
			UserID:      row.UserID,
			Role:        hmodel.Role(row.Role),
			DisplayName: row.DisplayName,
			JoinedAt:    row.JoinedAt,
			UpdatedAt:   row.UpdatedAt,
			Version:     row.Version,
		}
	}
	return out, nil
}

func (r changes) Categories(
	ctx context.Context, householdID uuid.UUID, cursor int64, limit int32,
) ([]cmodel.Category, error) {
	rows, err := r.q.ListCategoryChanges(ctx, queries.ListCategoryChangesParams{
		HouseholdID: householdID, Version: cursor, Limit: limit,
	})
	if err != nil {
		return nil, fmt.Errorf("list category changes: %w", err)
	}
	out := make([]cmodel.Category, len(rows))
	for i, row := range rows {
		out[i] = cmodel.Category{
			ID:          row.ID,
			HouseholdID: row.HouseholdID,
			BuiltinKey:  row.BuiltinKey,
			Name:        row.Name,
			Icon:        row.Icon,
			Color:       row.Color,
			SortOrder:   row.SortOrder,
			UpdatedAt:   row.UpdatedAt,
			Version:     row.Version,
		}
	}
	return out, nil
}

func (r changes) Places(ctx context.Context, householdID uuid.UUID, cursor int64, limit int32) ([]pmodel.Place, error) {
	rows, err := r.q.ListPlaceChanges(ctx, queries.ListPlaceChangesParams{
		HouseholdID: householdID, Version: cursor, Limit: limit,
	})
	if err != nil {
		return nil, fmt.Errorf("list place changes: %w", err)
	}
	out := make([]pmodel.Place, len(rows))
	for i, row := range rows {
		out[i] = pmodel.Place{
			ID:          row.ID,
			HouseholdID: row.HouseholdID,
			Name:        row.Name,
			Lat:         row.Lat,
			Lng:         row.Lng,
			RadiusM:     row.RadiusM,
			CategoryID:  row.CategoryID,
			SortOrder:   row.SortOrder,
			UpdatedAt:   row.UpdatedAt,
			Version:     row.Version,
		}
	}
	return out, nil
}

func (r changes) Todos(ctx context.Context, householdID uuid.UUID, cursor int64, limit int32) ([]tmodel.Todo, error) {
	rows, err := r.q.ListTodoChanges(ctx, queries.ListTodoChangesParams{
		HouseholdID: householdID, Version: cursor, Limit: limit,
	})
	if err != nil {
		return nil, fmt.Errorf("list todo changes: %w", err)
	}
	out := make([]tmodel.Todo, len(rows))
	for i, row := range rows {
		out[i] = tmodel.Todo{
			ID:          row.ID,
			HouseholdID: row.HouseholdID,
			PlaceID:     row.PlaceID,
			Title:       row.Title,
			AssigneeID:  row.AssigneeID,
			CompletedAt: row.CompletedAt,
			UpdatedAt:   row.UpdatedAt,
			Version:     row.Version,
		}
	}
	return out, nil
}

func (r changes) Deletions(
	ctx context.Context, householdID uuid.UUID, cursor int64, limit int32,
) ([]model.Deletion, error) {
	rows, err := r.q.ListDeletions(ctx, queries.ListDeletionsParams{
		HouseholdID: householdID, Version: cursor, Limit: limit,
	})
	if err != nil {
		return nil, fmt.Errorf("list deletions: %w", err)
	}
	out := make([]model.Deletion, len(rows))
	for i, row := range rows {
		out[i] = model.Deletion{Kind: model.Kind(row.TableName), ID: row.RowID, Version: row.Version}
	}
	return out, nil
}
