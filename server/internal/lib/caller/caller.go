// Package caller carries the authenticated user of a request.
package caller

import (
	"context"

	"github.com/google/uuid"
)

type contextKey struct{}

func Set(ctx context.Context, userID uuid.UUID) context.Context {
	return context.WithValue(ctx, contextKey{}, userID)
}

func UserID(ctx context.Context) (uuid.UUID, bool) {
	id, ok := ctx.Value(contextKey{}).(uuid.UUID)
	return id, ok
}
