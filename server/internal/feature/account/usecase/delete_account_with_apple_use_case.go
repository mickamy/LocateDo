package usecase

import (
	"context"
	"errors"
	"fmt"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/account/model"
	"github.com/mickamy/LocateDo/internal/infra/apple"
	"github.com/mickamy/LocateDo/internal/lib/clock"
)

type DeleteAccountWithAppleInput struct {
	IdentityToken string
	Nonce         string
}

type DeleteAccountWithAppleOutput struct {
	Deleted bool
}

// DeleteAccountWithApple deletes the account behind an identity token issued
// to the website, so people can delete their account without the app.
type DeleteAccountWithApple struct {
	_             di.Infra       `di:"embed"`
	deleteAccount *DeleteAccount `di:""`
	apple         apple.Auth     `di:""`
}

func (uc DeleteAccountWithApple) Do(
	ctx context.Context,
	in DeleteAccountWithAppleInput,
) (DeleteAccountWithAppleOutput, error) {
	identity, err := uc.apple.VerifyWebIdentityToken(ctx, in.IdentityToken, in.Nonce, clock.Now(ctx))
	if errors.Is(err, apple.ErrInvalidToken) {
		return DeleteAccountWithAppleOutput{}, aerrors.Unauthenticated(err.Error())
	}
	if err != nil {
		return DeleteAccountWithAppleOutput{}, fmt.Errorf("verify identity token: %w", err)
	}

	deleted, err := uc.deleteAccount.deleteIdentity(ctx, model.ProviderApple, identity.Subject)
	if err != nil {
		return DeleteAccountWithAppleOutput{}, err
	}
	return DeleteAccountWithAppleOutput{Deleted: deleted}, nil
}
