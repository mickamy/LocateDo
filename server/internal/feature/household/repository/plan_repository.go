package repository

import (
	"context"
	"fmt"
	"uuid"

	"github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/household/queries"
	"github.com/mickamy/LocateDo/internal/infra/storage/db"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
)

type Plan interface {
	// Set reports whether the plan changed.
	Set(ctx context.Context, householdID uuid.UUID, plan model.Plan) (bool, error)
	Owners(ctx context.Context, plan model.Plan) ([]uuid.UUID, error)
	Bind(tx tx.Tx) Plan
}

type plan struct {
	q *queries.Queries
}

var _ Plan = plan{}

func NewPlan(reader db.Reader) Plan {
	return plan{q: queries.New(reader)}
}

func (r plan) Bind(tx tx.Tx) Plan {
	return plan{q: queries.New(tx.DBTX())}
}

func (r plan) Set(ctx context.Context, householdID uuid.UUID, p model.Plan) (bool, error) {
	n, err := r.q.SetHouseholdPlan(ctx, queries.SetHouseholdPlanParams{ID: householdID, Plan: string(p)})
	if err != nil {
		return false, fmt.Errorf("set plan: %w", err)
	}
	return n > 0, nil
}

func (r plan) Owners(ctx context.Context, p model.Plan) ([]uuid.UUID, error) {
	ids, err := r.q.ListHouseholdOwnersByPlan(ctx, string(p))
	if err != nil {
		return nil, fmt.Errorf("list owners by plan: %w", err)
	}
	return ids, nil
}
