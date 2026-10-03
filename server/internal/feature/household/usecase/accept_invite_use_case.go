package usecase

import (
	"context"
	"errors"
	"fmt"
	"uuid"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/household/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/internal/lib/token"
)

type AcceptInviteInput struct {
	UserID uuid.UUID
	Token  string
}

type AcceptInviteOutput struct {
	Household model.Household
}

// AcceptInvite moves the caller into the inviting household. A caller alone in
// a household of their own brings its contents along; one sharing a household
// with others must clear it first. Any refusal rolls back, leaving the invite
// unused.
type AcceptInvite struct {
	_           di.Infra              `di:"embed"`
	transactor  tx.Transactor         `di:""`
	households  repository.Household  `di:""`
	memberships repository.Membership `di:""`
	invites     repository.Invite     `di:""`
}

func (uc AcceptInvite) Do(ctx context.Context, in AcceptInviteInput) (AcceptInviteOutput, error) {
	now := clock.Now(ctx)

	var target model.Household
	if err := uc.transactor.WithTx(ctx, func(tx tx.Tx) error {
		households := uc.households.Bind(tx)
		memberships := uc.memberships.Bind(tx)

		inv, err := uc.invites.Bind(tx).Accept(ctx, token.HashOpaque(in.Token), in.UserID, now)
		if err != nil {
			return fmt.Errorf("use invite: %w", err)
		}
		if !now.Before(inv.ExpiresAt) {
			return aerrors.Precondition("invite expired")
		}

		target, err = households.Find(ctx, inv.HouseholdID)
		if err != nil {
			return fmt.Errorf("find household: %w", err)
		}
		n, err := memberships.Count(ctx, target.ID)
		if err != nil {
			return fmt.Errorf("count members: %w", err)
		}
		if n >= model.MaxMembers {
			return aerrors.Precondition("household is full")
		}

		if err := uc.leaveCurrent(ctx, households, memberships, in.UserID, target.ID); err != nil {
			return err
		}
		if err := memberships.Create(ctx, model.Membership{
			HouseholdID: target.ID,
			UserID:      in.UserID,
			Role:        model.RoleMember,
		}); err != nil {
			return fmt.Errorf("create membership: %w", err)
		}
		return nil
	}); err != nil {
		return AcceptInviteOutput{}, fmt.Errorf("accept invite: %w", err)
	}
	return AcceptInviteOutput{Household: target}, nil
}

func (uc AcceptInvite) leaveCurrent(
	ctx context.Context,
	households repository.Household,
	memberships repository.Membership,
	userID, targetID uuid.UUID,
) error {
	current, err := memberships.FindByUser(ctx, userID)
	if errors.Is(err, aerrors.ErrNotFound) {
		return nil
	}
	if err != nil {
		return fmt.Errorf("find membership: %w", err)
	}
	if current.HouseholdID == targetID {
		return aerrors.Conflict("already a member")
	}

	n, err := memberships.Count(ctx, current.HouseholdID)
	if err != nil {
		return fmt.Errorf("count members: %w", err)
	}
	if n > 1 {
		return aerrors.Precondition("current household has other members")
	}

	if err := households.MoveContents(ctx, current.HouseholdID, targetID); err != nil {
		return fmt.Errorf("move contents: %w", err)
	}
	if err := households.Delete(ctx, current.HouseholdID); err != nil {
		return fmt.Errorf("delete old household: %w", err)
	}
	return nil
}
