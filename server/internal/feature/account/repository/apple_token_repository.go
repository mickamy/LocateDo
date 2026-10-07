package repository

import (
	"context"
	"errors"
	"fmt"
	"uuid"

	"github.com/jackc/pgx/v5"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/account/model"
	"github.com/mickamy/LocateDo/internal/feature/account/queries"
	"github.com/mickamy/LocateDo/internal/infra/apple"
	"github.com/mickamy/LocateDo/internal/infra/storage/db"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
)

// AppleToken stores Apple's refresh token, already sealed by the caller.
type AppleToken interface {
	Save(ctx context.Context, userID uuid.UUID, token model.AppleToken) error
	Find(ctx context.Context, userID uuid.UUID) (model.AppleToken, error)
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

func (r appleToken) Save(ctx context.Context, userID uuid.UUID, token model.AppleToken) error {
	err := r.q.UpsertAppleToken(ctx, queries.UpsertAppleTokenParams{
		UserID:                 userID,
		RefreshTokenCiphertext: token.Sealed,
		Client:                 string(token.Client),
	})
	if err != nil {
		return fmt.Errorf("upsert apple token: %w", err)
	}
	return nil
}

func (r appleToken) Find(ctx context.Context, userID uuid.UUID) (model.AppleToken, error) {
	row, err := r.q.GetAppleToken(ctx, userID)
	if errors.Is(err, pgx.ErrNoRows) {
		return model.AppleToken{}, aerrors.NotFound("apple token")
	}
	if err != nil {
		return model.AppleToken{}, fmt.Errorf("get apple token: %w", err)
	}
	return model.AppleToken{Sealed: row.RefreshTokenCiphertext, Client: apple.ClientKind(row.Client)}, nil
}
