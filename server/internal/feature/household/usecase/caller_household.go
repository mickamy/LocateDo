package usecase

import (
	"context"
	"errors"
	"fmt"
	"uuid"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/household/repository"
)

// CallerHousehold returns the household the caller belongs to; other features
// use it to scope their writes.
func CallerHousehold(ctx context.Context, memberships repository.Membership, userID uuid.UUID) (uuid.UUID, error) {
	m, err := memberships.FindByUser(ctx, userID)
	if errors.Is(err, aerrors.ErrNotFound) {
		return uuid.UUID{}, aerrors.PermissionDenied("not a member of a household")
	}
	if err != nil {
		return uuid.UUID{}, fmt.Errorf("find membership: %w", err)
	}
	return m.HouseholdID, nil
}
