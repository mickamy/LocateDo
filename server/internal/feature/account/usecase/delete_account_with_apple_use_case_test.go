package usecase_test

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/account/model"
	"github.com/mickamy/LocateDo/internal/feature/account/repository"
	"github.com/mickamy/LocateDo/internal/feature/account/usecase"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestDeleteAccountWithApple(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	infra, _ := fakedApple(d)
	_, err := usecase.NewSignInWithApple(infra, newLib()).Do(fixedClock(t), signInInput("apple-sub", ""))
	require.NoError(t, err)

	// act
	out, err := usecase.NewDeleteAccountWithApple(infra).Do(fixedClock(t), webAppleInput("web:apple-sub"))

	// assert
	require.NoError(t, err)
	assert.True(t, out.Deleted)
	_, err = repository.NewUser(d.Reader).FindByIdentity(t.Context(), model.ProviderApple, "apple-sub")
	require.ErrorIs(t, err, aerrors.ErrNotFound)
	var messages int
	require.NoError(t, d.Writer.QueryRow(t.Context(), "SELECT count(*) FROM outbox_messages").Scan(&messages))
	assert.Equal(t, 1, messages, "the Apple token is revoked as with in-app deletion")
}

func TestDeleteAccountWithApple_noAccount(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	infra, _ := fakedApple(d)

	// act
	out, err := usecase.NewDeleteAccountWithApple(infra).Do(fixedClock(t), webAppleInput("web:apple-sub"))

	// assert
	require.NoError(t, err)
	assert.False(t, out.Deleted)
	var users int
	require.NoError(t, d.Writer.QueryRow(t.Context(), "SELECT count(*) FROM users").Scan(&users))
	assert.Zero(t, users, "no account is created")
}

func TestDeleteAccountWithApple_appToken(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	infra, _ := fakedApple(d)
	_, err := usecase.NewSignInWithApple(infra, newLib()).Do(fixedClock(t), signInInput("apple-sub", ""))
	require.NoError(t, err)

	// act: a token issued to the app is not accepted here
	_, err = usecase.NewDeleteAccountWithApple(infra).Do(fixedClock(t), webAppleInput("identity:apple-sub"))

	// assert
	require.ErrorIs(t, err, aerrors.ErrUnauthenticated)
	_, err = repository.NewUser(d.Reader).FindByIdentity(t.Context(), model.ProviderApple, "apple-sub")
	require.NoError(t, err)
}
