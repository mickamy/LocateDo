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
)

type RemoveMemberInput struct {
	CallerID    uuid.UUID
	HouseholdID uuid.UUID
	UserID      uuid.UUID
}

// RemoveMember lets a member leave and the owner remove others. The owner
// cannot leave; deleting the account takes the household with it.
type RemoveMember struct {
	_           di.Infra              `di:"embed"`
	transactor  tx.Transactor         `di:""`
	memberships repository.Membership `di:""`
}

func (uc RemoveMember) Do(ctx context.Context, in RemoveMemberInput) error {
	if err := uc.transactor.WithTx(ctx, func(tx tx.Tx) error {
		memberships := uc.memberships.Bind(tx)

		caller, err := memberships.FindByUser(ctx, in.CallerID)
		if errors.Is(err, aerrors.ErrNotFound) || (err == nil && caller.HouseholdID != in.HouseholdID) {
			return aerrors.PermissionDenied("not a member of this household")
		}
		if err != nil {
			return fmt.Errorf("find membership: %w", err)
		}

		switch {
		case in.UserID == in.CallerID && caller.Role == model.RoleOwner:
			return aerrors.Precondition("the owner cannot leave")
		case in.UserID != in.CallerID && caller.Role != model.RoleOwner:
			return aerrors.PermissionDenied("only the owner can remove others")
		}
		if err := memberships.Delete(ctx, in.HouseholdID, in.UserID); err != nil {
			return fmt.Errorf("delete membership: %w", err)
		}
		if err := memberships.ReleaseAssignments(ctx, in.HouseholdID, in.UserID); err != nil {
			return fmt.Errorf("release assignments: %w", err)
		}
		return nil
	}); err != nil {
		return fmt.Errorf("remove member: %w", err)
	}
	return nil
}
