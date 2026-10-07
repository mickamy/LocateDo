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
	"github.com/mickamy/LocateDo/internal/infra/apple"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/internal/lib/seal"
	"github.com/mickamy/LocateDo/internal/lib/token"
)

type SignInWithAppleInput struct {
	IdentityToken     string
	AuthorizationCode string
	Nonce             string
	DisplayName       string
	Client            apple.ClientKind
}

type SignInWithAppleOutput struct {
	Session     model.Session
	HouseholdID *uuid.UUID
}

type SignInWithApple struct {
	_           di.Infra                `di:"embed"`
	_           di.Lib                  `di:"embed"`
	transactor  tx.Transactor           `di:""`
	users       repository.User         `di:""`
	tokens      repository.RefreshToken `di:""`
	memberships hrepository.Membership  `di:""`
	appleTokens repository.AppleToken   `di:""`
	apple       apple.Auth              `di:""`
	signer      token.Signer            `di:""`
	box         seal.Box                `di:""`
}

func (uc SignInWithApple) Do(ctx context.Context, in SignInWithAppleInput) (SignInWithAppleOutput, error) {
	now := clock.Now(ctx)

	identity, err := uc.apple.VerifyIdentityToken(ctx, in.Client, in.IdentityToken, in.Nonce, now)
	if errors.Is(err, apple.ErrInvalidToken) {
		return SignInWithAppleOutput{}, aerrors.Unauthenticated(err.Error())
	}
	if err != nil {
		return SignInWithAppleOutput{}, fmt.Errorf("verify identity token: %w", err)
	}

	appleRefresh, err := uc.apple.ExchangeCode(ctx, in.Client, in.AuthorizationCode, now)
	if err != nil {
		return SignInWithAppleOutput{}, fmt.Errorf("exchange authorization code: %w", err)
	}

	var session model.Session
	var householdID *uuid.UUID
	if err := uc.transactor.WithTx(ctx, func(tx tx.Tx) error {
		user, newUser, err := findOrCreateUser(ctx, uc.users.Bind(tx), model.ProviderApple, identity.Subject, in.DisplayName)
		if err != nil {
			return err
		}

		appleToken := model.AppleToken{Sealed: uc.box.Seal([]byte(appleRefresh), user.ID[:]), Client: in.Client}
		if err := uc.appleTokens.Bind(tx).Save(ctx, user.ID, appleToken); err != nil {
			return fmt.Errorf("save apple token: %w", err)
		}

		session, err = startSession(ctx, uc.tokens.Bind(tx), uc.signer, user.ID, uuid.NewV7(), now)
		if err != nil {
			return err
		}
		session.NewUser = newUser

		householdID, err = householdOf(ctx, uc.memberships.Bind(tx), user.ID)
		return err
	}); err != nil {
		return SignInWithAppleOutput{}, fmt.Errorf("sign in with apple: %w", err)
	}
	return SignInWithAppleOutput{Session: session, HouseholdID: householdID}, nil
}
