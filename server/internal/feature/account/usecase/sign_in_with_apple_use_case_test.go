package usecase_test

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/account/model"
	"github.com/mickamy/LocateDo/internal/lib/token"
)

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
