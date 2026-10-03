package usecase

import (
	"context"
	"errors"
	"fmt"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/account/model"
	"github.com/mickamy/LocateDo/internal/feature/account/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/internal/lib/token"
)

type RefreshTokenInput struct {
	RefreshToken string
}

type RefreshTokenOutput struct {
	Session model.Session
}

// RefreshToken rotates a refresh token. Presenting one that was already used
// means it leaked, so the whole family is revoked and the device must sign in
// again.
type RefreshToken struct {
	_          di.Infra                `di:"embed"`
	_          di.Lib                  `di:"embed"`
	transactor tx.Transactor           `di:""`
	tokens     repository.RefreshToken `di:""`
	signer     token.Signer            `di:""`
}

func (uc RefreshToken) Do(ctx context.Context, in RefreshTokenInput) (RefreshTokenOutput, error) {
	now := clock.Now(ctx)
	hash := token.HashOpaque(in.RefreshToken)

	var session model.Session
	// Set when the request is refused but the transaction must still commit,
	// so that a family revoked for reuse stays revoked.
	var rejected error
	if err := uc.transactor.WithTx(ctx, func(tx tx.Tx) error {
		tokens := uc.tokens.Bind(tx)

		current, err := tokens.Use(ctx, hash, now)
		if errors.Is(err, aerrors.ErrNotFound) {
			rejected, err = uc.reject(ctx, tokens, hash)
			return err
		}
		if err != nil {
			return fmt.Errorf("use refresh token: %w", err)
		}
		if !now.Before(current.ExpiresAt) {
			return aerrors.Unauthenticated("refresh token expired")
		}

		session, err = startSession(ctx, tokens, uc.signer, current.UserID, current.FamilyID, now)
		return err
	}); err != nil {
		return RefreshTokenOutput{}, fmt.Errorf("refresh token: %w", err)
	}
	if rejected != nil {
		return RefreshTokenOutput{}, rejected
	}
	return RefreshTokenOutput{Session: session}, nil
}

// reject explains why an unusable token was refused, revoking its family when
// the token was already used.
func (uc RefreshToken) reject(ctx context.Context, tokens repository.RefreshToken, hash []byte) (error, error) {
	used, err := tokens.FindByHash(ctx, hash)
	if errors.Is(err, aerrors.ErrNotFound) {
		return aerrors.Unauthenticated("unknown refresh token"), nil
	}
	if err != nil {
		return nil, fmt.Errorf("find refresh token: %w", err)
	}
	if err := tokens.RevokeFamily(ctx, used.FamilyID); err != nil {
		return nil, fmt.Errorf("revoke refresh token family: %w", err)
	}
	return aerrors.Unauthenticated("refresh token reused"), nil
}
