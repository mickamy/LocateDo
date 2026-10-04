package usecase_test

import (
	"testing"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/account/model"
	"github.com/mickamy/LocateDo/internal/feature/account/repository"
	"github.com/mickamy/LocateDo/internal/feature/account/usecase"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestDeleteAccount(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	infra, fake := fakedApple(d)
	lib := newLib()
	signedIn, err := usecase.NewSignInWithApple(infra, lib).Do(fixedClock(t), signInInput("apple-sub", ""))
	require.NoError(t, err)

	// act
	err = usecase.NewDeleteAccount(infra, lib).Do(fixedClock(t), usecase.DeleteAccountInput{
		UserID: signedIn.Session.UserID,
	})

	// assert
	require.NoError(t, err)
	assert.Equal(t, []string{"apple-refresh:auth-code"}, fake.revokedTokens())
	_, err = repository.NewUser(d.Reader).FindByIdentity(t.Context(), model.ProviderApple, "apple-sub")
	require.ErrorIs(t, err, aerrors.ErrNotFound)
	_, err = usecase.NewRefreshToken(infra, lib).Do(fixedClock(t), usecase.RefreshTokenInput{
		RefreshToken: signedIn.Session.RefreshToken,
	})
	require.ErrorIs(t, err, aerrors.ErrUnauthenticated)
}

func TestDeleteAccount_revokeFails(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	infra, fake := fakedApple(d)
	lib := newLib()
	signedIn, err := usecase.NewSignInWithApple(infra, lib).Do(fixedClock(t), signInInput("apple-sub", ""))
	require.NoError(t, err)
	fake.failRevoke = true

	// act
	err = usecase.NewDeleteAccount(infra, lib).Do(fixedClock(t), usecase.DeleteAccountInput{
		UserID: signedIn.Session.UserID,
	})

	// assert
	require.Error(t, err)
	_, err = repository.NewUser(d.Reader).FindByIdentity(t.Context(), model.ProviderApple, "apple-sub")
	require.NoError(t, err)
}

func TestDeleteAccount_unknownUser(t *testing.T) {
	t.Parallel()

	d := tdb.New(t)
	infra, fake := fakedApple(d)

	err := usecase.NewDeleteAccount(infra, newLib()).Do(fixedClock(t), usecase.DeleteAccountInput{UserID: uuid.NewV7()})

	require.ErrorIs(t, err, aerrors.ErrNotFound)
	assert.Empty(t, fake.revokedTokens())
}
