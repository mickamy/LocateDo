package usecase

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"uuid"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/account/model"
	"github.com/mickamy/LocateDo/internal/feature/account/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/internal/outbox"
)

type DeleteAccountInput struct {
	UserID uuid.UUID
}

// DeleteAccount deletes the user and, in the same transaction, leaves the
// worker a message to revoke their Apple token, as App Store review requires.
// Owned households, tokens, and memberships go with the user through
// foreign-key cascades, so the message carries the sealed token itself.
type DeleteAccount struct {
	_           di.Infra              `di:"embed"`
	transactor  tx.Transactor         `di:""`
	users       repository.User       `di:""`
	appleTokens repository.AppleToken `di:""`
	messages    outbox.Repository     `di:""`
}

func (uc DeleteAccount) Do(ctx context.Context, in DeleteAccountInput) error {
	if err := uc.transactor.WithTx(ctx, func(tx tx.Tx) error {
		return uc.deleteUser(ctx, tx, in.UserID)
	}); err != nil {
		return fmt.Errorf("delete account: %w", err)
	}
	return nil
}

// deleteIdentity deletes the user behind a provider identity and reports
// whether there was one.
func (uc DeleteAccount) deleteIdentity(ctx context.Context, provider model.Provider, subject string) (bool, error) {
	deleted := false
	if err := uc.transactor.WithTx(ctx, func(tx tx.Tx) error {
		user, err := uc.users.Bind(tx).FindByIdentity(ctx, provider, subject)
		if errors.Is(err, aerrors.ErrNotFound) {
			return nil
		}
		if err != nil {
			return fmt.Errorf("find user: %w", err)
		}
		deleted = true
		return uc.deleteUser(ctx, tx, user.ID)
	}); err != nil {
		return false, fmt.Errorf("delete %s account: %w", provider, err)
	}
	return deleted, nil
}

func (uc DeleteAccount) deleteUser(ctx context.Context, tx tx.Tx, userID uuid.UUID) error {
	appleToken, err := uc.appleTokens.Bind(tx).Find(ctx, userID)
	if err != nil && !errors.Is(err, aerrors.ErrNotFound) {
		return fmt.Errorf("find apple token: %w", err)
	}
	if err == nil {
		payload, err := json.Marshal(model.AppleRevocation{
			UserID:      userID,
			SealedToken: appleToken.Sealed,
			Client:      appleToken.Client,
		})
		if err != nil {
			return fmt.Errorf("encode revocation: %w", err)
		}
		if err := uc.messages.Bind(tx).Enqueue(ctx, outbox.Message{
			Kind:    outbox.KindRevokeAppleToken,
			Payload: payload,
			RunAt:   clock.Now(ctx),
		}); err != nil {
			return fmt.Errorf("enqueue revocation: %w", err)
		}
	}
	if err := uc.users.Bind(tx).Delete(ctx, userID); err != nil {
		return fmt.Errorf("delete user: %w", err)
	}
	return nil
}
