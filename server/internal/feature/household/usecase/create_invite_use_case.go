package usecase

import (
	"context"
	"fmt"
	"time"
	"uuid"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/household/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/internal/lib/token"
)

type CreateInviteInput struct {
	UserID      uuid.UUID
	HouseholdID uuid.UUID
}

type CreateInviteOutput struct {
	Token     string
	ExpiresAt time.Time
}

type CreateInvite struct {
	_           di.Infra              `di:"embed"`
	transactor  tx.Transactor         `di:""`
	households  repository.Household  `di:""`
	memberships repository.Membership `di:""`
	invites     repository.Invite     `di:""`
}

func (uc CreateInvite) Do(ctx context.Context, in CreateInviteInput) (CreateInviteOutput, error) {
	now := clock.Now(ctx)
	raw, hash := token.NewOpaque()
	expiresAt := now.Add(model.InviteTTL)

	if err := uc.transactor.WithTx(ctx, func(tx tx.Tx) error {
		membership, err := uc.memberships.Bind(tx).FindByUser(ctx, in.UserID)
		if err != nil {
			return fmt.Errorf("find membership: %w", err)
		}
		if membership.HouseholdID != in.HouseholdID || membership.Role != model.RoleOwner {
			return aerrors.PermissionDenied("only the owner can invite")
		}

		h, err := uc.households.Bind(tx).Find(ctx, in.HouseholdID)
		if err != nil {
			return fmt.Errorf("find household: %w", err)
		}
		if h.Plan != model.PlanPro {
			return aerrors.Precondition("sharing needs pro")
		}

		n, err := uc.memberships.Bind(tx).Count(ctx, in.HouseholdID)
		if err != nil {
			return fmt.Errorf("count members: %w", err)
		}
		if n >= model.MaxMembers {
			return aerrors.Precondition("household is full")
		}

		if err := uc.invites.Bind(tx).Create(ctx, model.Invite{
			HouseholdID: in.HouseholdID,
			CreatedBy:   in.UserID,
			ExpiresAt:   expiresAt,
		}, hash); err != nil {
			return fmt.Errorf("store invite: %w", err)
		}
		return nil
	}); err != nil {
		return CreateInviteOutput{}, fmt.Errorf("create invite: %w", err)
	}
	return CreateInviteOutput{Token: raw, ExpiresAt: expiresAt}, nil
}
