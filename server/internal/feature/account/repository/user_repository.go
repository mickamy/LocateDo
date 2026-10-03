package repository

import (
	"context"
	"errors"
	"fmt"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/account/model"
	"github.com/mickamy/LocateDo/internal/feature/account/queries"
	"github.com/mickamy/LocateDo/internal/infra/storage/db"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
)

type User interface {
	FindByIdentity(ctx context.Context, provider model.Provider, subject string) (model.User, error)
	Create(ctx context.Context, displayName string) (model.User, error)
	AddIdentity(ctx context.Context, userID uuid.UUID, provider model.Provider, subject string) error
	Delete(ctx context.Context, id uuid.UUID) error
	Bind(tx tx.Tx) User
}

type user struct {
	q *queries.Queries
}

var _ User = user{}

func NewUser(reader db.Reader) User {
	return user{q: queries.New(reader)}
}

func (r user) Bind(tx tx.Tx) User {
	return user{q: queries.New(tx.DBTX())}
}

func (r user) FindByIdentity(ctx context.Context, provider model.Provider, subject string) (model.User, error) {
	row, err := r.q.GetUserByIdentity(ctx, queries.GetUserByIdentityParams{
		Provider: string(provider),
		Subject:  subject,
	})
	if errors.Is(err, pgx.ErrNoRows) {
		return model.User{}, aerrors.NotFound("user")
	}
	if err != nil {
		return model.User{}, fmt.Errorf("get user by identity: %w", err)
	}
	return toUser(row), nil
}

func (r user) Create(ctx context.Context, displayName string) (model.User, error) {
	row, err := r.q.CreateUser(ctx, displayName)
	if err != nil {
		return model.User{}, fmt.Errorf("create user: %w", err)
	}
	return toUser(row), nil
}

func (r user) AddIdentity(ctx context.Context, userID uuid.UUID, provider model.Provider, subject string) error {
	err := r.q.CreateUserIdentity(ctx, queries.CreateUserIdentityParams{
		UserID:   userID,
		Provider: string(provider),
		Subject:  subject,
	})
	if db.IsUniqueViolation(err) {
		return aerrors.Conflict("user identity")
	}
	if err != nil {
		return fmt.Errorf("create user identity: %w", err)
	}
	return nil
}

func (r user) Delete(ctx context.Context, id uuid.UUID) error {
	n, err := r.q.DeleteUser(ctx, id)
	if err != nil {
		return fmt.Errorf("delete user: %w", err)
	}
	if n == 0 {
		return aerrors.NotFound("user")
	}
	return nil
}

func toUser(row queries.User) model.User {
	return model.User{
		ID:          row.ID,
		DisplayName: row.DisplayName,
		CreatedAt:   row.CreatedAt,
	}
}
