package repository

import (
	"context"
	"errors"
	"fmt"
	"uuid"

	"github.com/jackc/pgx/v5"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/account/queries"
	"github.com/mickamy/LocateDo/internal/infra/storage/db"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
)

// AppleToken stores Apple's refresh token, already sealed by the caller.
type AppleToken interface {
	Save(ctx context.Context, userID uuid.UUID, sealed []byte) error
	Find(ctx context.Context, userID uuid.UUID) ([]byte, error)
	Bind(tx tx.Tx) AppleToken
}

type appleToken struct {
	q *queries.Queries
}

var _ AppleToken = appleToken{}

func NewAppleToken(reader db.Reader) AppleToken {
	return appleToken{q: queries.New(reader)}
}

func (r appleToken) Bind(tx tx.Tx) AppleToken {
	return appleToken{q: queries.New(tx.DBTX())}
}

func (r appleToken) Save(ctx context.Context, userID uuid.UUID, sealed []byte) error {
	err := r.q.UpsertAppleToken(ctx, queries.UpsertAppleTokenParams{
		UserID:                 userID,
		RefreshTokenCiphertext: sealed,
	})
	if err != nil {
		return fmt.Errorf("upsert apple token: %w", err)
	}
	return nil
}

func (r appleToken) Find(ctx context.Context, userID uuid.UUID) ([]byte, error) {
	sealed, err := r.q.GetAppleToken(ctx, userID)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, aerrors.NotFound("apple token")
	}
	if err != nil {
		return nil, fmt.Errorf("get apple token: %w", err)
	}
	return sealed, nil
}
