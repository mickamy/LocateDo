package usecase

import (
	"context"
	"errors"
	"fmt"

	"github.com/google/uuid"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/account/repository"
	"github.com/mickamy/LocateDo/internal/infra/apple"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/internal/lib/seal"
)

type DeleteAccountInput struct {
	UserID uuid.UUID
}

// DeleteAccount revokes the user's Apple token before deleting the user, as
// App Store review requires. If the revocation fails nothing is deleted, so
// the device can retry. Owned households, tokens, and memberships go with the
// user through foreign-key cascades.
type DeleteAccount struct {
	_           di.Infra              `di:"embed"`
	_           di.Lib                `di:"embed"`
	transactor  tx.Transactor         `di:""`
	users       repository.User       `di:""`
	appleTokens repository.AppleToken `di:""`
	apple       apple.Auth            `di:""`
	box         seal.Box              `di:""`
}

func (uc DeleteAccount) Do(ctx context.Context, in DeleteAccountInput) error {
	// TODO: Move the revocation to an outbox so the deletion and the revoke job
	// commit together and a worker retries the revoke; for now an Apple outage
	// blocks deletion.
	if err := uc.revokeApple(ctx, in.UserID); err != nil {
		return err
	}

	if err := uc.transactor.WithTx(ctx, func(tx tx.Tx) error {
		return uc.users.Bind(tx).Delete(ctx, in.UserID)
	}); err != nil {
		return fmt.Errorf("delete account: %w", err)
	}
	return nil
}

func (uc DeleteAccount) revokeApple(ctx context.Context, userID uuid.UUID) error {
	sealed, err := uc.appleTokens.Find(ctx, userID)
	if errors.Is(err, aerrors.ErrNotFound) {
		return nil
	}
	if err != nil {
		return fmt.Errorf("find apple token: %w", err)
	}

	refreshToken, err := uc.box.Open(sealed, userID[:])
	if err != nil {
		return fmt.Errorf("open apple token: %w", err)
	}
	if err := uc.apple.Revoke(ctx, string(refreshToken), clock.Now(ctx)); err != nil {
		return fmt.Errorf("revoke apple token: %w", err)
	}
	return nil
}
