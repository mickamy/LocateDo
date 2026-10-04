// Package caller carries the authenticated user of a request and the
// household they belong to.
package caller

import (
	"context"
	"uuid"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
)

type (
	userKey      struct{}
	householdKey struct{}
)

func Set(ctx context.Context, userID uuid.UUID) context.Context {
	return context.WithValue(ctx, userKey{}, userID)
}

func UserID(ctx context.Context) (uuid.UUID, error) {
	id, ok := ctx.Value(userKey{}).(uuid.UUID)
	if !ok {
		return uuid.UUID{}, aerrors.Unauthenticated("no caller")
	}
	return id, nil
}

func SetHousehold(ctx context.Context, householdID uuid.UUID) context.Context {
	return context.WithValue(ctx, householdKey{}, householdID)
}

func HouseholdID(ctx context.Context) (uuid.UUID, error) {
	id, ok := ctx.Value(householdKey{}).(uuid.UUID)
	if !ok {
		return uuid.UUID{}, aerrors.PermissionDenied("not a member of a household")
	}
	return id, nil
}
