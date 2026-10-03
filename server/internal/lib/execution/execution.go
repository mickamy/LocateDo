package execution

import (
	"context"
	"uuid"
)

type contextKey struct{}

func NewID() (uuid.UUID, error) {
	id := uuid.NewV7()
	return id, nil
}

func Get(ctx context.Context) uuid.UUID {
	id, ok := ctx.Value(contextKey{}).(uuid.UUID)
	if !ok {
		return uuid.Nil()
	}
	return id
}

func Set(ctx context.Context, id uuid.UUID) context.Context {
	return context.WithValue(ctx, contextKey{}, id)
}
