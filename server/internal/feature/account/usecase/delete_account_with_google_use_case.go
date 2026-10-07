package usecase

import (
	"context"
	"errors"
	"fmt"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/account/model"
	"github.com/mickamy/LocateDo/internal/infra/google"
	"github.com/mickamy/LocateDo/internal/lib/clock"
)

type DeleteAccountWithGoogleInput struct {
	IDToken string
	Nonce   string
}

type DeleteAccountWithGoogleOutput struct {
	Deleted bool
}

// DeleteAccountWithGoogle deletes the account behind a Google id token, so
// people can delete their account from the website without the app.
type DeleteAccountWithGoogle struct {
	_             di.Infra       `di:"embed"`
	deleteAccount *DeleteAccount `di:""`
	google        google.Auth    `di:""`
}

func (uc DeleteAccountWithGoogle) Do(
	ctx context.Context,
	in DeleteAccountWithGoogleInput,
) (DeleteAccountWithGoogleOutput, error) {
	identity, err := uc.google.VerifyIDToken(ctx, in.IDToken, in.Nonce, clock.Now(ctx))
	if errors.Is(err, google.ErrInvalidToken) {
		return DeleteAccountWithGoogleOutput{}, aerrors.Unauthenticated(err.Error())
	}
	if err != nil {
		return DeleteAccountWithGoogleOutput{}, fmt.Errorf("verify id token: %w", err)
	}

	deleted, err := uc.deleteAccount.deleteIdentity(ctx, model.ProviderGoogle, identity.Subject)
	if err != nil {
		return DeleteAccountWithGoogleOutput{}, err
	}
	return DeleteAccountWithGoogleOutput{Deleted: deleted}, nil
}
