package usecase_test

import (
	"testing"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/account/model"
	"github.com/mickamy/LocateDo/internal/feature/account/usecase"
)

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

	err := e.deleteAccount.Do(e.ctx, usecase.DeleteAccountInput{UserID: uuid.NewV7()})

	require.ErrorIs(t, err, aerrors.ErrNotFound)
	assert.Empty(t, e.apple.revokedTokens())
}
