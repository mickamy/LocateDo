package usecase_test

import (
	"encoding/json"
	"testing"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/account/model"
	"github.com/mickamy/LocateDo/internal/feature/account/repository"
	"github.com/mickamy/LocateDo/internal/feature/account/usecase"
	"github.com/mickamy/LocateDo/internal/outbox"
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
	userID := signedIn.Session.UserID

	// act
	err = usecase.NewDeleteAccount(infra).Do(fixedClock(t), usecase.DeleteAccountInput{UserID: userID})

	// assert
	require.NoError(t, err)
	_, err = repository.NewUser(d.Reader).FindByIdentity(t.Context(), model.ProviderApple, "apple-sub")
	require.ErrorIs(t, err, aerrors.ErrNotFound)
	_, err = usecase.NewRefreshToken(infra, lib).Do(fixedClock(t), usecase.RefreshTokenInput{
		RefreshToken: signedIn.Session.RefreshToken,
	})
	require.ErrorIs(t, err, aerrors.ErrUnauthenticated)

	assert.Empty(t, fake.revokedTokens(), "Apple is not called inline")
	var kind string
	var payload []byte
	require.NoError(t, d.Writer.QueryRow(t.Context(), "SELECT kind, payload FROM outbox_messages").Scan(&kind, &payload))
	assert.Equal(t, string(outbox.KindRevokeAppleToken), kind)
	var revocation model.AppleRevocation
	require.NoError(t, json.Unmarshal(payload, &revocation))
	assert.Equal(t, userID, revocation.UserID)
	plain, err := lib.Box.Open(revocation.SealedToken, userID[:])
	require.NoError(t, err)
	assert.Equal(t, "apple-refresh:auth-code", string(plain), "the message carries the token the cascade deletes")
}

func TestDeleteAccount_withoutAppleToken(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	userID := d.Seeder.User(t)

	// act
	err := usecase.NewDeleteAccount(d.Infra()).Do(fixedClock(t), usecase.DeleteAccountInput{UserID: userID})

	// assert
	require.NoError(t, err)
	var messages int
	require.NoError(t, d.Writer.QueryRow(t.Context(), "SELECT count(*) FROM outbox_messages").Scan(&messages))
	assert.Zero(t, messages, "nothing to revoke")
}

func TestDeleteAccount_unknownUser(t *testing.T) {
	t.Parallel()

	d := tdb.New(t)

	err := usecase.NewDeleteAccount(d.Infra()).Do(fixedClock(t), usecase.DeleteAccountInput{UserID: uuid.NewV7()})

	require.ErrorIs(t, err, aerrors.ErrNotFound)
}
