package usecase

import (
	"context"
	"errors"
	"fmt"
	"uuid"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/account/model"
	"github.com/mickamy/LocateDo/internal/feature/account/repository"
	hrepository "github.com/mickamy/LocateDo/internal/feature/household/repository"
	"github.com/mickamy/LocateDo/internal/infra/google"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/internal/lib/token"
)

type SignInWithGoogleInput struct {
	IDToken string
	Nonce   string
}

type SignInWithGoogleOutput struct {
	Session     model.Session
	HouseholdID *uuid.UUID
}

// SignInWithGoogle is the Android counterpart of SignInWithApple. Google needs
// no code exchange and nothing to revoke on deletion; the display name comes
// from the id token.
type SignInWithGoogle struct {
	_           di.Infra                `di:"embed"`
	_           di.Lib                  `di:"embed"`
	transactor  tx.Transactor           `di:""`
	users       repository.User         `di:""`
	tokens      repository.RefreshToken `di:""`
	memberships hrepository.Membership  `di:""`
	google      google.Auth             `di:""`
	signer      token.Signer            `di:""`
}

func (uc SignInWithGoogle) Do(ctx context.Context, in SignInWithGoogleInput) (SignInWithGoogleOutput, error) {
	now := clock.Now(ctx)

	identity, err := uc.google.VerifyIDToken(ctx, in.IDToken, in.Nonce, now)
	if errors.Is(err, google.ErrInvalidToken) {
		return SignInWithGoogleOutput{}, aerrors.Unauthenticated(err.Error())
	}
	if err != nil {
		return SignInWithGoogleOutput{}, fmt.Errorf("verify id token: %w", err)
	}

	var session model.Session
	var householdID *uuid.UUID
	if err := uc.transactor.WithTx(ctx, func(tx tx.Tx) error {
		user, newUser, err := findOrCreateUser(ctx, uc.users.Bind(tx), model.ProviderGoogle, identity.Subject, identity.Name)
		if err != nil {
			return err
		}

		session, err = startSession(ctx, uc.tokens.Bind(tx), uc.signer, user.ID, uuid.NewV7(), now)
		if err != nil {
			return err
		}
		session.NewUser = newUser

		householdID, err = householdOf(ctx, uc.memberships.Bind(tx), user.ID)
		return err
	}); err != nil {
		return SignInWithGoogleOutput{}, fmt.Errorf("sign in with google: %w", err)
	}
	return SignInWithGoogleOutput{Session: session, HouseholdID: householdID}, nil
}
