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

type Membership interface {
	// Create fails with a conflict when the user already belongs to a household.
	Create(ctx context.Context, m model.Membership) error
	// FindByUser returns the user's only membership; other features use it to
	// learn which household the caller may touch.
	FindByUser(ctx context.Context, userID uuid.UUID) (model.Membership, error)
	Count(ctx context.Context, householdID uuid.UUID) (int, error)
	Delete(ctx context.Context, householdID, userID uuid.UUID) error
	Bind(tx tx.Tx) Membership
}

type membership struct {
	q *queries.Queries
}

var _ Membership = membership{}

func NewMembership(reader db.Reader) Membership {
	return membership{q: queries.New(reader)}
}

func (r membership) Bind(tx tx.Tx) Membership {
	return membership{q: queries.New(tx.DBTX())}
}

func (r membership) Create(ctx context.Context, m model.Membership) error {
	err := r.q.CreateMembership(ctx, queries.CreateMembershipParams{
		HouseholdID: m.HouseholdID,
		UserID:      m.UserID,
		Role:        string(m.Role),
	})
	if db.IsUniqueViolation(err) {
		return aerrors.Conflict("membership")
	}
	if err != nil {
		return fmt.Errorf("create membership: %w", err)
	}
	return nil
}

func (r membership) FindByUser(ctx context.Context, userID uuid.UUID) (model.Membership, error) {
	row, err := r.q.GetMembershipByUser(ctx, userID)
	if errors.Is(err, pgx.ErrNoRows) {
		return model.Membership{}, aerrors.NotFound("membership")
	}
	if err != nil {
		return model.Membership{}, fmt.Errorf("get membership: %w", err)
	}
	return model.Membership{
		HouseholdID: row.HouseholdID,
		UserID:      row.UserID,
		Role:        model.Role(row.Role),
		DisplayName: row.DisplayName,
		JoinedAt:    row.JoinedAt,
		UpdatedAt:   row.UpdatedAt,
		Version:     row.Version,
	}, nil
}

func (r membership) Count(ctx context.Context, householdID uuid.UUID) (int, error) {
	n, err := r.q.CountMemberships(ctx, householdID)
	if err != nil {
		return 0, fmt.Errorf("count memberships: %w", err)
	}
	return int(n), nil
}

func (r membership) Delete(ctx context.Context, householdID, userID uuid.UUID) error {
	n, err := r.q.DeleteMembership(ctx, queries.DeleteMembershipParams{
		HouseholdID: householdID,
		UserID:      userID,
	})
	if err != nil {
		return fmt.Errorf("delete membership: %w", err)
	}
	if n == 0 {
		return aerrors.NotFound("membership")
	}
	return nil
}
