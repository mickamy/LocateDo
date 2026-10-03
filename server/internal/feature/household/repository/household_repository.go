package repository

import (
	"context"
	"errors"
	"fmt"

	"github.com/google/uuid"
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
	Delete(ctx context.Context, id uuid.UUID) error
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
		ID:        row.ID,
		OwnerID:   row.OwnerID,
		Plan:      model.Plan(row.Plan),
		CreatedAt: row.CreatedAt,
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
		ID:        row.ID,
		OwnerID:   row.OwnerID,
		Plan:      model.Plan(row.Plan),
		CreatedAt: row.CreatedAt,
	}, nil
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
