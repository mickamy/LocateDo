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

func TestDeleteAccountWithGoogle(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	infra := fakedGoogle(d)
	_, err := usecase.NewSignInWithGoogle(infra, newLib()).Do(fixedClock(t), googleInput("google-sub"))
	require.NoError(t, err)

	// act
	out, err := usecase.NewDeleteAccountWithGoogle(infra).Do(fixedClock(t), usecase.DeleteAccountWithGoogleInput{
		IDToken: "google:google-sub",
		Nonce:   "0123456789abcdef",
	})

	// assert
	require.NoError(t, err)
	assert.True(t, out.Deleted)
	_, err = repository.NewUser(d.Reader).FindByIdentity(t.Context(), model.ProviderGoogle, "google-sub")
	require.ErrorIs(t, err, aerrors.ErrNotFound)
}

func TestDeleteAccountWithGoogle_noAccount(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)

	// act
	out, err := usecase.NewDeleteAccountWithGoogle(fakedGoogle(d)).Do(fixedClock(t), usecase.DeleteAccountWithGoogleInput{
		IDToken: "google:google-sub",
		Nonce:   "0123456789abcdef",
	})

	// assert
	require.NoError(t, err)
	assert.False(t, out.Deleted)
	var users int
	require.NoError(t, d.Writer.QueryRow(t.Context(), "SELECT count(*) FROM users").Scan(&users))
	assert.Zero(t, users, "no account is created")
}

func TestDeleteAccountWithGoogle_invalidToken(t *testing.T) {
	t.Parallel()

	d := tdb.New(t)

	_, err := usecase.NewDeleteAccountWithGoogle(fakedGoogle(d)).Do(fixedClock(t), usecase.DeleteAccountWithGoogleInput{
		IDToken: "garbage",
		Nonce:   "0123456789abcdef",
	})

	require.ErrorIs(t, err, aerrors.ErrUnauthenticated)
}
