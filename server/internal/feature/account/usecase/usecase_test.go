package usecase_test

import (
	"context"
	"errors"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/account/model"
	"github.com/mickamy/LocateDo/internal/feature/account/repository"
	"github.com/mickamy/LocateDo/internal/feature/account/usecase"
	"github.com/mickamy/LocateDo/internal/infra/apple"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/internal/lib/seal"
	"github.com/mickamy/LocateDo/internal/lib/token"
	"github.com/mickamy/LocateDo/test/tinfra"
)

var now = time.Date(2026, 10, 3, 12, 0, 0, 0, time.UTC)

func TestSignInWithApple_newUser(t *testing.T) {
	t.Parallel()

	// arrange
	e := newEnv(t)

	// act
	out, err := e.signIn.Do(e.ctx, signInInput("apple-sub", "Tetsuro"))

	// assert
	require.NoError(t, err)
	s := out.Session
	assert.True(t, s.NewUser)
	assert.Equal(t, now.Add(token.AccessTTL), s.AccessTokenExpiresAt)
	userID, err := e.signer.VerifyAccess(s.AccessToken, now)
	require.NoError(t, err)
	assert.Equal(t, s.UserID, userID)

	user, err := e.users.FindByIdentity(t.Context(), model.ProviderApple, "apple-sub")
	require.NoError(t, err)
	assert.Equal(t, s.UserID, user.ID)
	assert.Equal(t, "Tetsuro", user.DisplayName)
	assert.Equal(t, "apple-refresh:auth-code", e.openAppleToken(t, user.ID))
}

func TestSignInWithApple_existingUser(t *testing.T) {
	t.Parallel()

	// arrange
	e := newEnv(t)
	first, err := e.signIn.Do(e.ctx, signInInput("apple-sub", "Tetsuro"))
	require.NoError(t, err)
	in := signInInput("apple-sub", "")
	in.AuthorizationCode = "second-code"

	// act
	second, err := e.signIn.Do(e.ctx, in)

	// assert
	require.NoError(t, err)
	assert.False(t, second.Session.NewUser)
	assert.Equal(t, first.Session.UserID, second.Session.UserID)
	assert.NotEqual(t, first.Session.RefreshToken, second.Session.RefreshToken)
	assert.Equal(t, "apple-refresh:second-code", e.openAppleToken(t, first.Session.UserID))
}

func TestSignInWithApple_invalidIdentityToken(t *testing.T) {
	t.Parallel()

	// arrange
	e := newEnv(t)
	in := signInInput("apple-sub", "")
	in.IdentityToken = "forged"

	// act
	_, err := e.signIn.Do(e.ctx, in)

	// assert
	require.ErrorIs(t, err, aerrors.ErrUnauthenticated)
	_, err = e.users.FindByIdentity(t.Context(), model.ProviderApple, "apple-sub")
	require.ErrorIs(t, err, aerrors.ErrNotFound)
}

func TestSignInWithApple_exchangeFails(t *testing.T) {
	t.Parallel()

	// arrange
	e := newEnv(t)
	in := signInInput("apple-sub", "")
	in.AuthorizationCode = "expired-code"

	// act
	_, err := e.signIn.Do(e.ctx, in)

	// assert
	require.Error(t, err)
	_, err = e.users.FindByIdentity(t.Context(), model.ProviderApple, "apple-sub")
	require.ErrorIs(t, err, aerrors.ErrNotFound)
}

func TestRefreshToken_rotates(t *testing.T) {
	t.Parallel()

	// arrange
	e := newEnv(t)
	signedIn, err := e.signIn.Do(e.ctx, signInInput("apple-sub", ""))
	require.NoError(t, err)
	later := clock.Set(t.Context(), clock.NewFixed(now.Add(2*time.Hour)))

	// act
	out, err := e.refresh.Do(later, usecase.RefreshTokenInput{RefreshToken: signedIn.Session.RefreshToken})

	// assert
	require.NoError(t, err)
	assert.Equal(t, signedIn.Session.UserID, out.Session.UserID)
	assert.False(t, out.Session.NewUser)
	assert.NotEqual(t, signedIn.Session.RefreshToken, out.Session.RefreshToken)
	assert.Equal(t, now.Add(2*time.Hour+token.AccessTTL), out.Session.AccessTokenExpiresAt)
}

func TestRefreshToken_reuseRevokesFamily(t *testing.T) {
	t.Parallel()

	// arrange
	e := newEnv(t)
	signedIn, err := e.signIn.Do(e.ctx, signInInput("apple-sub", ""))
	require.NoError(t, err)
	stolen := signedIn.Session.RefreshToken
	rotated, err := e.refresh.Do(e.ctx, usecase.RefreshTokenInput{RefreshToken: stolen})
	require.NoError(t, err)

	// act
	_, reuseErr := e.refresh.Do(e.ctx, usecase.RefreshTokenInput{RefreshToken: stolen})
	_, rotatedErr := e.refresh.Do(e.ctx, usecase.RefreshTokenInput{RefreshToken: rotated.Session.RefreshToken})

	// assert
	require.ErrorIs(t, reuseErr, aerrors.ErrUnauthenticated)
	require.ErrorContains(t, reuseErr, "reused")
	require.ErrorIs(t, rotatedErr, aerrors.ErrUnauthenticated)
}

func TestRefreshToken_reuseKeepsOtherFamilies(t *testing.T) {
	t.Parallel()

	// arrange: the same user signed in on two devices
	e := newEnv(t)
	phone, err := e.signIn.Do(e.ctx, signInInput("apple-sub", ""))
	require.NoError(t, err)
	tablet, err := e.signIn.Do(e.ctx, signInInput("apple-sub", ""))
	require.NoError(t, err)
	_, err = e.refresh.Do(e.ctx, usecase.RefreshTokenInput{RefreshToken: phone.Session.RefreshToken})
	require.NoError(t, err)

	// act
	_, reuseErr := e.refresh.Do(e.ctx, usecase.RefreshTokenInput{RefreshToken: phone.Session.RefreshToken})
	_, tabletErr := e.refresh.Do(e.ctx, usecase.RefreshTokenInput{RefreshToken: tablet.Session.RefreshToken})

	// assert
	require.ErrorIs(t, reuseErr, aerrors.ErrUnauthenticated)
	require.NoError(t, tabletErr)
}

func TestRefreshToken_unknown(t *testing.T) {
	t.Parallel()

	e := newEnv(t)

	_, err := e.refresh.Do(e.ctx, usecase.RefreshTokenInput{RefreshToken: "never-issued"})

	require.ErrorIs(t, err, aerrors.ErrUnauthenticated)
}

func TestRefreshToken_expired(t *testing.T) {
	t.Parallel()

	// arrange
	e := newEnv(t)
	signedIn, err := e.signIn.Do(e.ctx, signInInput("apple-sub", ""))
	require.NoError(t, err)
	expired := clock.Set(t.Context(), clock.NewFixed(now.Add(token.RefreshTTL)))

	// act
	_, err = e.refresh.Do(expired, usecase.RefreshTokenInput{RefreshToken: signedIn.Session.RefreshToken})

	// assert
	require.ErrorIs(t, err, aerrors.ErrUnauthenticated)
	// refused without being consumed, so it still works before expiry
	_, err = e.refresh.Do(e.ctx, usecase.RefreshTokenInput{RefreshToken: signedIn.Session.RefreshToken})
	require.NoError(t, err)
}

func TestDeleteAccount(t *testing.T) {
	t.Parallel()

	// arrange
	e := newEnv(t)
	signedIn, err := e.signIn.Do(e.ctx, signInInput("apple-sub", ""))
	require.NoError(t, err)

	// act
	err = e.deleteAccount.Do(e.ctx, usecase.DeleteAccountInput{UserID: signedIn.Session.UserID})

	// assert
	require.NoError(t, err)
	assert.Equal(t, []string{"apple-refresh:auth-code"}, e.apple.revokedTokens())
	_, err = e.users.FindByIdentity(t.Context(), model.ProviderApple, "apple-sub")
	require.ErrorIs(t, err, aerrors.ErrNotFound)
	_, err = e.refresh.Do(e.ctx, usecase.RefreshTokenInput{RefreshToken: signedIn.Session.RefreshToken})
	require.ErrorIs(t, err, aerrors.ErrUnauthenticated)
}

func TestDeleteAccount_revokeFails(t *testing.T) {
	t.Parallel()

	// arrange
	e := newEnv(t)
	signedIn, err := e.signIn.Do(e.ctx, signInInput("apple-sub", ""))
	require.NoError(t, err)
	e.apple.failRevoke = true

	// act
	err = e.deleteAccount.Do(e.ctx, usecase.DeleteAccountInput{UserID: signedIn.Session.UserID})

	// assert
	require.Error(t, err)
	_, err = e.users.FindByIdentity(t.Context(), model.ProviderApple, "apple-sub")
	require.NoError(t, err)
}

func TestDeleteAccount_unknownUser(t *testing.T) {
	t.Parallel()

	e := newEnv(t)

	err := e.deleteAccount.Do(e.ctx, usecase.DeleteAccountInput{UserID: uuid.Must(uuid.NewV7())})

	require.ErrorIs(t, err, aerrors.ErrNotFound)
	assert.Empty(t, e.apple.revokedTokens())
}

type env struct {
	ctx           context.Context //nolint:containedctx // test fixture
	users         repository.User
	appleTokens   repository.AppleToken
	signer        token.Signer
	box           seal.Box
	apple         *fakeApple
	signIn        *usecase.SignInWithApple
	refresh       *usecase.RefreshToken
	deleteAccount *usecase.DeleteAccount
}

func newEnv(t *testing.T) *env {
	t.Helper()

	infra := tinfra.New(t)
	fake := &fakeApple{}
	infra.Apple = fake
	lib := di.MustNewLib(di.NewConfig())

	return &env{
		ctx:           clock.Set(t.Context(), clock.NewFixed(now)),
		users:         repository.NewUser(infra.Reader),
		appleTokens:   repository.NewAppleToken(infra.Reader),
		signer:        lib.Signer,
		box:           lib.Box,
		apple:         fake,
		signIn:        usecase.NewSignInWithApple(infra, lib),
		refresh:       usecase.NewRefreshToken(infra, lib),
		deleteAccount: usecase.NewDeleteAccount(infra, lib),
	}
}

func (e *env) openAppleToken(t *testing.T, userID uuid.UUID) string {
	t.Helper()

	sealed, err := e.appleTokens.Find(t.Context(), userID)
	require.NoError(t, err)
	plain, err := e.box.Open(sealed, userID[:])
	require.NoError(t, err)
	return string(plain)
}

func signInInput(subject, displayName string) usecase.SignInWithAppleInput {
	return usecase.SignInWithAppleInput{
		IdentityToken:     "identity:" + subject,
		AuthorizationCode: "auth-code",
		Nonce:             "raw-nonce",
		DisplayName:       displayName,
	}
}

// fakeApple accepts identity tokens of the form "identity:<subject>" and
// exchanges any code except "expired-code" for "apple-refresh:<code>".
type fakeApple struct {
	mu         sync.Mutex
	failRevoke bool
	revoked    []string
}

var _ apple.Auth = (*fakeApple)(nil)

func (f *fakeApple) VerifyIdentityToken(_ context.Context, raw, rawNonce string, _ time.Time) (apple.Identity, error) {
	subject, ok := strings.CutPrefix(raw, "identity:")
	if !ok || rawNonce == "" {
		return apple.Identity{}, apple.ErrInvalidToken
	}
	return apple.Identity{Subject: subject}, nil
}

func (f *fakeApple) ExchangeCode(_ context.Context, code string, _ time.Time) (string, error) {
	if code == "expired-code" {
		return "", errors.New("status 400: invalid_grant")
	}
	return "apple-refresh:" + code, nil
}

func (f *fakeApple) Revoke(_ context.Context, refreshToken string, _ time.Time) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.failRevoke {
		return errors.New("status 503")
	}
	f.revoked = append(f.revoked, refreshToken)
	return nil
}

func (f *fakeApple) revokedTokens() []string {
	f.mu.Lock()
	defer f.mu.Unlock()
	return f.revoked
}
