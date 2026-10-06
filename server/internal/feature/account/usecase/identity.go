package usecase

import (
	"context"
	"errors"
	"fmt"
	"uuid"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/account/model"
	"github.com/mickamy/LocateDo/internal/feature/account/repository"
	hrepository "github.com/mickamy/LocateDo/internal/feature/household/repository"
)

// findOrCreateUser returns the user behind a provider identity, creating one
// on the first sign-in; the bool reports a creation.
func findOrCreateUser(
	ctx context.Context,
	users repository.User,
	provider model.Provider,
	subject, displayName string,
) (model.User, bool, error) {
	user, err := users.FindByIdentity(ctx, provider, subject)
	if err == nil {
		return user, false, nil
	}
	if !errors.Is(err, aerrors.ErrNotFound) {
		return model.User{}, false, fmt.Errorf("find user: %w", err)
	}

	user, err = users.Create(ctx, displayName)
	if err != nil {
		return model.User{}, false, fmt.Errorf("create user: %w", err)
	}
	if err := users.AddIdentity(ctx, user.ID, provider, subject); err != nil {
		return model.User{}, false, fmt.Errorf("add identity: %w", err)
	}
	return user, true, nil
}

// householdOf is the household the user belongs to, or nil before CreateHousehold.
func householdOf(ctx context.Context, memberships hrepository.Membership, userID uuid.UUID) (*uuid.UUID, error) {
	m, err := memberships.FindByUser(ctx, userID)
	if errors.Is(err, aerrors.ErrNotFound) {
		return nil, nil //nolint:nilnil // no household is a regular outcome
	}
	if err != nil {
		return nil, fmt.Errorf("find membership: %w", err)
	}
	return &m.HouseholdID, nil
}
