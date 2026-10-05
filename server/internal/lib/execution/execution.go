package execution

import (
	"context"
	"uuid"
)

type (
	idKey      struct{}
	jobNameKey struct{}
)

func NewID() (uuid.UUID, error) {
	id := uuid.NewV7()
	return id, nil
}

func GetID(ctx context.Context) uuid.UUID {
	id, ok := ctx.Value(idKey{}).(uuid.UUID)
	if !ok {
		return uuid.Nil()
	}
	return id
}

func SetID(ctx context.Context, id uuid.UUID) context.Context {
	return context.WithValue(ctx, idKey{}, id)
}

func SetJobName(ctx context.Context, name string) context.Context {
	return context.WithValue(ctx, jobNameKey{}, name)
}

func JobName(ctx context.Context) string {
	name, _ := ctx.Value(jobNameKey{}).(string)
	return name
}
