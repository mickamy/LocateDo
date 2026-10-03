package usecase

import (
	"context"
	"errors"
	"fmt"

	"github.com/google/uuid"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/account/model"
	"github.com/mickamy/LocateDo/internal/feature/account/repository"
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
}

type SignInWithAppleOutput struct {
	Session model.Session
}

type SignInWithApple struct {
	_           di.Infra                `di:"embed"`
	_           di.Lib                  `di:"embed"`
	transactor  tx.Transactor           `di:""`
	users       repository.User         `di:""`
	tokens      repository.RefreshToken `di:""`
	appleTokens repository.AppleToken   `di:""`
	apple       apple.Auth              `di:""`
	signer      token.Signer            `di:""`
	box         seal.Box                `di:""`
}

func (uc SignInWithApple) Do(ctx context.Context, in SignInWithAppleInput) (SignInWithAppleOutput, error) {
	now := clock.Now(ctx)

	identity, err := uc.apple.VerifyIdentityToken(ctx, in.IdentityToken, in.Nonce, now)
	if errors.Is(err, apple.ErrInvalidToken) {
		return SignInWithAppleOutput{}, aerrors.Unauthenticated(err.Error())
	}
	if err != nil {
		return SignInWithAppleOutput{}, fmt.Errorf("verify identity token: %w", err)
	}

	appleRefresh, err := uc.apple.ExchangeCode(ctx, in.AuthorizationCode, now)
	if err != nil {
		return SignInWithAppleOutput{}, fmt.Errorf("exchange authorization code: %w", err)
	}

	var session model.Session
	if err := uc.transactor.WithTx(ctx, func(tx tx.Tx) error {
		user, newUser, err := uc.findOrCreate(ctx, tx, identity.Subject, in.DisplayName)
		if err != nil {
			return err
		}

		sealed := uc.box.Seal([]byte(appleRefresh), user.ID[:])
		if err := uc.appleTokens.Bind(tx).Save(ctx, user.ID, sealed); err != nil {
			return fmt.Errorf("save apple token: %w", err)
		}

		session, err = startSession(ctx, uc.tokens.Bind(tx), uc.signer, user.ID, uuid.Must(uuid.NewV7()), now)
		if err != nil {
			return err
		}
		session.NewUser = newUser
		return nil
	}); err != nil {
		return SignInWithAppleOutput{}, fmt.Errorf("sign in with apple: %w", err)
	}
	return SignInWithAppleOutput{Session: session}, nil
}

func (uc SignInWithApple) findOrCreate(
	ctx context.Context,
	tx tx.Tx,
	subject, displayName string,
) (model.User, bool, error) {
	users := uc.users.Bind(tx)

	user, err := users.FindByIdentity(ctx, model.ProviderApple, subject)
	if err == nil {
		return user, false, nil
	}
	if !errors.Is(err, aerrors.ErrNotFound) {
		return model.User{}, false, fmt.Errorf("find user: %w", err)
	}

	user, err = users.Create(ctx, displayName)
	if err != nil {
		return model.User{}, false, fmt.Errorf("create user: %w", err)
	}
	if err := users.AddIdentity(ctx, user.ID, model.ProviderApple, subject); err != nil {
		return model.User{}, false, fmt.Errorf("add identity: %w", err)
	}
	return user, true, nil
}
