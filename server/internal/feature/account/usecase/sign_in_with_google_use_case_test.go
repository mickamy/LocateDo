package usecase_test

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/account/model"
	"github.com/mickamy/LocateDo/internal/feature/account/repository"
	"github.com/mickamy/LocateDo/internal/feature/account/usecase"
	hmodel "github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/lib/token"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestSignInWithGoogle_newUser(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	lib := newLib()

	// act
	out, err := usecase.NewSignInWithGoogle(fakedGoogle(d), lib).Do(fixedClock(t), googleInput("google-sub"))

	// assert
	require.NoError(t, err)
	s := out.Session
	assert.True(t, s.NewUser)
	assert.Nil(t, out.HouseholdID)
	assert.Equal(t, now.Add(token.AccessTTL), s.AccessTokenExpiresAt)
	userID, err := lib.Signer.VerifyAccess(s.AccessToken, now)
	require.NoError(t, err)
	assert.Equal(t, s.UserID, userID)

	user, err := repository.NewUser(d.Reader).FindByIdentity(t.Context(), model.ProviderGoogle, "google-sub")
	require.NoError(t, err)
	assert.Equal(t, s.UserID, user.ID)
	assert.Equal(t, "Google User", user.DisplayName, "the name comes from the id token")
}

func TestSignInWithGoogle_existingUser(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	signIn := usecase.NewSignInWithGoogle(fakedGoogle(d), newLib())
	first, err := signIn.Do(fixedClock(t), googleInput("google-sub"))
	require.NoError(t, err)

	// act
	second, err := signIn.Do(fixedClock(t), googleInput("google-sub"))

	// assert
	require.NoError(t, err)
	assert.False(t, second.Session.NewUser)
	assert.Equal(t, first.Session.UserID, second.Session.UserID)
	assert.NotEqual(t, first.Session.RefreshToken, second.Session.RefreshToken)
}

func TestSignInWithGoogle_keepsAppleAndGoogleApart(t *testing.T) {
	t.Parallel()

	// arrange: the same subject string under two providers is two users
	d := tdb.New(t)
	infra := fakedGoogle(d)
	infra.Apple = &fakeApple{}
	apple, err := usecase.NewSignInWithApple(infra, newLib()).Do(fixedClock(t), signInInput("shared-sub", ""))
	require.NoError(t, err)

	// act
	google, err := usecase.NewSignInWithGoogle(infra, newLib()).Do(fixedClock(t), googleInput("shared-sub"))

	// assert
	require.NoError(t, err)
	assert.True(t, google.Session.NewUser)
	assert.NotEqual(t, apple.Session.UserID, google.Session.UserID)
}

func TestSignInWithGoogle_invalidIDToken(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	in := googleInput("google-sub")
	in.IDToken = "forged"

	// act
	_, err := usecase.NewSignInWithGoogle(fakedGoogle(d), newLib()).Do(fixedClock(t), in)

	// assert
	require.ErrorIs(t, err, aerrors.ErrUnauthenticated)
	_, err = repository.NewUser(d.Reader).FindByIdentity(t.Context(), model.ProviderGoogle, "google-sub")
	require.ErrorIs(t, err, aerrors.ErrNotFound)
}

func TestSignInWithGoogle_returnsHousehold(t *testing.T) {
	t.Parallel()

	// arrange: signed in once on another device and joined a household there
	d := tdb.New(t)
	signIn := usecase.NewSignInWithGoogle(fakedGoogle(d), newLib())
	first, err := signIn.Do(fixedClock(t), googleInput("google-sub"))
	require.NoError(t, err)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	d.Seeder.Join(t, h.ID, first.Session.UserID)

	// act
	out, err := signIn.Do(fixedClock(t), googleInput("google-sub"))

	// assert
	require.NoError(t, err)
	require.NotNil(t, out.HouseholdID)
	assert.Equal(t, h.ID, *out.HouseholdID)
}
