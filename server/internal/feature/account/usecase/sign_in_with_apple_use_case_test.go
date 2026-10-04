package usecase_test

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/account/model"
	"github.com/mickamy/LocateDo/internal/feature/account/repository"
	"github.com/mickamy/LocateDo/internal/feature/account/usecase"
	"github.com/mickamy/LocateDo/internal/lib/token"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestSignInWithApple_newUser(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	infra, _ := fakedApple(d)
	lib := newLib()

	// act
	out, err := usecase.NewSignInWithApple(infra, lib).Do(fixedClock(t), signInInput("apple-sub", "Tetsuro"))

	// assert
	require.NoError(t, err)
	s := out.Session
	assert.True(t, s.NewUser)
	assert.Equal(t, now.Add(token.AccessTTL), s.AccessTokenExpiresAt)
	userID, err := lib.Signer.VerifyAccess(s.AccessToken, now)
	require.NoError(t, err)
	assert.Equal(t, s.UserID, userID)

	user, err := repository.NewUser(d.Reader).FindByIdentity(t.Context(), model.ProviderApple, "apple-sub")
	require.NoError(t, err)
	assert.Equal(t, s.UserID, user.ID)
	assert.Equal(t, "Tetsuro", user.DisplayName)
	assert.Equal(t, "apple-refresh:auth-code", openAppleToken(t, d, lib, user.ID))
}

func TestSignInWithApple_existingUser(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	infra, _ := fakedApple(d)
	lib := newLib()
	signIn := usecase.NewSignInWithApple(infra, lib)
	first, err := signIn.Do(fixedClock(t), signInInput("apple-sub", "Tetsuro"))
	require.NoError(t, err)
	in := signInInput("apple-sub", "")
	in.AuthorizationCode = "second-code"

	// act
	second, err := signIn.Do(fixedClock(t), in)

	// assert
	require.NoError(t, err)
	assert.False(t, second.Session.NewUser)
	assert.Equal(t, first.Session.UserID, second.Session.UserID)
	assert.NotEqual(t, first.Session.RefreshToken, second.Session.RefreshToken)
	assert.Equal(t, "apple-refresh:second-code", openAppleToken(t, d, lib, first.Session.UserID))
}

func TestSignInWithApple_invalidIdentityToken(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	infra, _ := fakedApple(d)
	in := signInInput("apple-sub", "")
	in.IdentityToken = "forged"

	// act
	_, err := usecase.NewSignInWithApple(infra, newLib()).Do(fixedClock(t), in)

	// assert
	require.ErrorIs(t, err, aerrors.ErrUnauthenticated)
	_, err = repository.NewUser(d.Reader).FindByIdentity(t.Context(), model.ProviderApple, "apple-sub")
	require.ErrorIs(t, err, aerrors.ErrNotFound)
}

func TestSignInWithApple_exchangeFails(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	infra, _ := fakedApple(d)
	in := signInInput("apple-sub", "")
	in.AuthorizationCode = "expired-code"

	// act
	_, err := usecase.NewSignInWithApple(infra, newLib()).Do(fixedClock(t), in)

	// assert
	require.Error(t, err)
	_, err = repository.NewUser(d.Reader).FindByIdentity(t.Context(), model.ProviderApple, "apple-sub")
	require.ErrorIs(t, err, aerrors.ErrNotFound)
}
