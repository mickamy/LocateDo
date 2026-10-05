package execution

import (
	"context"
	"uuid"
)

type (
	contextKey struct{}
	jobNameKey struct{}
)

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

func SetJobName(ctx context.Context, name string) context.Context {
	return context.WithValue(ctx, jobNameKey{}, name)
}

func JobName(ctx context.Context) string {
	name, _ := ctx.Value(jobNameKey{}).(string)
	return name
}
