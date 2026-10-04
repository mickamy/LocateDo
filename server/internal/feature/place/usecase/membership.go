package usecase

import (
	"context"
	"errors"
	"fmt"
	"uuid"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	hrepository "github.com/mickamy/LocateDo/internal/feature/household/repository"
)

func callerHousehold(ctx context.Context, memberships hrepository.Membership, userID uuid.UUID) (uuid.UUID, error) {
	m, err := memberships.FindByUser(ctx, userID)
	if errors.Is(err, aerrors.ErrNotFound) {
		return uuid.UUID{}, aerrors.PermissionDenied("not a member of a household")
	}
	if err != nil {
		return uuid.UUID{}, fmt.Errorf("find membership: %w", err)
	}
	return m.HouseholdID, nil
}
