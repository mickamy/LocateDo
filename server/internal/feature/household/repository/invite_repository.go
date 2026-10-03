package repository

import (
	"context"
	"errors"
	"fmt"
	"time"
	"uuid"

	"github.com/jackc/pgx/v5"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/household/queries"
	"github.com/mickamy/LocateDo/internal/infra/storage/db"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
)

type Invite interface {
	Create(ctx context.Context, invite model.Invite, hash []byte) error
	// Accept marks an unused invite as used by userID. An unknown or already
	// used token is reported as not found; expiry is left to the caller.
	Accept(ctx context.Context, hash []byte, userID uuid.UUID, now time.Time) (model.Invite, error)
	Bind(tx tx.Tx) Invite
}

type invite struct {
	q *queries.Queries
}

var _ Invite = invite{}

func NewInvite(reader db.Reader) Invite {
	return invite{q: queries.New(reader)}
}

func (r invite) Bind(tx tx.Tx) Invite {
	return invite{q: queries.New(tx.DBTX())}
}

func (r invite) Create(ctx context.Context, inv model.Invite, hash []byte) error {
	err := r.q.CreateInvite(ctx, queries.CreateInviteParams{
		HouseholdID: inv.HouseholdID,
		TokenHash:   hash,
		CreatedBy:   inv.CreatedBy,
		ExpiresAt:   inv.ExpiresAt,
	})
	if err != nil {
		return fmt.Errorf("create invite: %w", err)
	}
	return nil
}

func (r invite) Accept(ctx context.Context, hash []byte, userID uuid.UUID, now time.Time) (model.Invite, error) {
	row, err := r.q.AcceptInvite(ctx, queries.AcceptInviteParams{
		AcceptedBy: &userID,
		AcceptedAt: &now,
		TokenHash:  hash,
	})
	if errors.Is(err, pgx.ErrNoRows) {
		return model.Invite{}, aerrors.NotFound("unused invite")
	}
	if err != nil {
		return model.Invite{}, fmt.Errorf("accept invite: %w", err)
	}
	return model.Invite{
		ID:          row.ID,
		HouseholdID: row.HouseholdID,
		CreatedBy:   row.CreatedBy,
		ExpiresAt:   row.ExpiresAt,
	}, nil
}
