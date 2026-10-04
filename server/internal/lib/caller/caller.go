// Package caller carries the authenticated user of a request.
package caller

import (
	"context"
	"uuid"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
)

type contextKey struct{}

func Set(ctx context.Context, userID uuid.UUID) context.Context {
	return context.WithValue(ctx, contextKey{}, userID)
}

func UserID(ctx context.Context) (uuid.UUID, error) {
	id, ok := ctx.Value(contextKey{}).(uuid.UUID)
	if !ok {
		return uuid.UUID{}, aerrors.Unauthenticated("no caller")
	}
	return id, nil
}
