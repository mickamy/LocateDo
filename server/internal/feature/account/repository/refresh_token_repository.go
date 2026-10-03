package repository

import (
	"context"
	"errors"
	"fmt"
	"time"
	"uuid"

	"github.com/jackc/pgx/v5"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/account/model"
	"github.com/mickamy/LocateDo/internal/feature/account/queries"
	"github.com/mickamy/LocateDo/internal/infra/storage/db"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
)

type RefreshToken interface {
	Create(ctx context.Context, token model.RefreshToken, hash []byte) error
	// Use marks an unused token as used. A token that is unknown or already
	// used is reported as not found; FindByHash tells the two apart.
	Use(ctx context.Context, hash []byte, now time.Time) (model.RefreshToken, error)
	FindByHash(ctx context.Context, hash []byte) (model.RefreshToken, error)
	RevokeFamily(ctx context.Context, familyID uuid.UUID) error
	Bind(tx tx.Tx) RefreshToken
}

type refreshToken struct {
	q *queries.Queries
}

var _ RefreshToken = refreshToken{}

func NewRefreshToken(reader db.Reader) RefreshToken {
	return refreshToken{q: queries.New(reader)}
}

func (r refreshToken) Bind(tx tx.Tx) RefreshToken {
	return refreshToken{q: queries.New(tx.DBTX())}
}

func (r refreshToken) Create(ctx context.Context, token model.RefreshToken, hash []byte) error {
	err := r.q.CreateRefreshToken(ctx, queries.CreateRefreshTokenParams{
		UserID:    token.UserID,
		FamilyID:  token.FamilyID,
		TokenHash: hash,
		ExpiresAt: token.ExpiresAt,
	})
	if err != nil {
		return fmt.Errorf("create refresh token: %w", err)
	}
	return nil
}

func (r refreshToken) Use(ctx context.Context, hash []byte, now time.Time) (model.RefreshToken, error) {
	row, err := r.q.UseRefreshToken(ctx, queries.UseRefreshTokenParams{
		UsedAt:    &now,
		TokenHash: hash,
	})
	if errors.Is(err, pgx.ErrNoRows) {
		return model.RefreshToken{}, aerrors.NotFound("unused refresh token")
	}
	if err != nil {
		return model.RefreshToken{}, fmt.Errorf("use refresh token: %w", err)
	}
	return model.RefreshToken{
		ID:        row.ID,
		UserID:    row.UserID,
		FamilyID:  row.FamilyID,
		ExpiresAt: row.ExpiresAt,
		UsedAt:    row.UsedAt,
	}, nil
}

func (r refreshToken) FindByHash(ctx context.Context, hash []byte) (model.RefreshToken, error) {
	row, err := r.q.GetRefreshTokenByHash(ctx, hash)
	if errors.Is(err, pgx.ErrNoRows) {
		return model.RefreshToken{}, aerrors.NotFound("refresh token")
	}
	if err != nil {
		return model.RefreshToken{}, fmt.Errorf("get refresh token: %w", err)
	}
	return model.RefreshToken{
		ID:        row.ID,
		UserID:    row.UserID,
		FamilyID:  row.FamilyID,
		ExpiresAt: row.ExpiresAt,
		UsedAt:    row.UsedAt,
	}, nil
}

func (r refreshToken) RevokeFamily(ctx context.Context, familyID uuid.UUID) error {
	if err := r.q.DeleteRefreshTokenFamily(ctx, familyID); err != nil {
		return fmt.Errorf("delete refresh token family: %w", err)
	}
	return nil
}
