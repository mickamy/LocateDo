package usecase_test

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/account/usecase"
	hmodel "github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/internal/lib/token"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestRefreshToken_rotates(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	infra, _ := fakedApple(d)
	lib := newLib()
	signedIn, err := usecase.NewSignInWithApple(infra, lib).Do(fixedClock(t), signInInput("apple-sub", ""))
	require.NoError(t, err)
	later := clock.Set(t.Context(), clock.NewFixed(now.Add(2*time.Hour)))

	// act
	out, err := usecase.NewRefreshToken(infra, lib).Do(later, usecase.RefreshTokenInput{
		RefreshToken: signedIn.Session.RefreshToken,
	})

	// assert
	require.NoError(t, err)
	assert.Equal(t, signedIn.Session.UserID, out.Session.UserID)
	assert.False(t, out.Session.NewUser)
	assert.NotEqual(t, signedIn.Session.RefreshToken, out.Session.RefreshToken)
	assert.Equal(t, now.Add(2*time.Hour+token.AccessTTL), out.Session.AccessTokenExpiresAt)
	assert.Nil(t, out.HouseholdID)
}

func TestRefreshToken_returnsHousehold(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	infra, _ := fakedApple(d)
	lib := newLib()
	signedIn, err := usecase.NewSignInWithApple(infra, lib).Do(fixedClock(t), signInInput("apple-sub", ""))
	require.NoError(t, err)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	d.Seeder.Join(t, h.ID, signedIn.Session.UserID)

	// act
	out, err := usecase.NewRefreshToken(infra, lib).Do(fixedClock(t), usecase.RefreshTokenInput{
		RefreshToken: signedIn.Session.RefreshToken,
	})

	// assert
	require.NoError(t, err)
	require.NotNil(t, out.HouseholdID)
	assert.Equal(t, h.ID, *out.HouseholdID)
}

func TestRefreshToken_reuseRevokesFamily(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	infra, _ := fakedApple(d)
	lib := newLib()
	refresh := usecase.NewRefreshToken(infra, lib)
	signedIn, err := usecase.NewSignInWithApple(infra, lib).Do(fixedClock(t), signInInput("apple-sub", ""))
	require.NoError(t, err)
	stolen := signedIn.Session.RefreshToken
	rotated, err := refresh.Do(fixedClock(t), usecase.RefreshTokenInput{RefreshToken: stolen})
	require.NoError(t, err)

	// act
	_, reuseErr := refresh.Do(fixedClock(t), usecase.RefreshTokenInput{RefreshToken: stolen})
	_, rotatedErr := refresh.Do(fixedClock(t), usecase.RefreshTokenInput{RefreshToken: rotated.Session.RefreshToken})

	// assert
	require.ErrorIs(t, reuseErr, aerrors.ErrUnauthenticated)
	require.ErrorContains(t, reuseErr, "reused")
	require.ErrorIs(t, rotatedErr, aerrors.ErrUnauthenticated)
}

func TestRefreshToken_reuseKeepsOtherFamilies(t *testing.T) {
	t.Parallel()

	// arrange: the same user signed in on two devices
	d := tdb.New(t)
	infra, _ := fakedApple(d)
	lib := newLib()
	signIn := usecase.NewSignInWithApple(infra, lib)
	refresh := usecase.NewRefreshToken(infra, lib)
	phone, err := signIn.Do(fixedClock(t), signInInput("apple-sub", ""))
	require.NoError(t, err)
	tablet, err := signIn.Do(fixedClock(t), signInInput("apple-sub", ""))
	require.NoError(t, err)
	_, err = refresh.Do(fixedClock(t), usecase.RefreshTokenInput{RefreshToken: phone.Session.RefreshToken})
	require.NoError(t, err)

	// act
	_, reuseErr := refresh.Do(fixedClock(t), usecase.RefreshTokenInput{RefreshToken: phone.Session.RefreshToken})
	_, tabletErr := refresh.Do(fixedClock(t), usecase.RefreshTokenInput{RefreshToken: tablet.Session.RefreshToken})

	// assert
	require.ErrorIs(t, reuseErr, aerrors.ErrUnauthenticated)
	require.NoError(t, tabletErr)
}

func TestRefreshToken_unknown(t *testing.T) {
	t.Parallel()

	d := tdb.New(t)

	_, err := usecase.NewRefreshToken(d.Infra(), newLib()).Do(fixedClock(t), usecase.RefreshTokenInput{
		RefreshToken: "never-issued",
	})

	require.ErrorIs(t, err, aerrors.ErrUnauthenticated)
}

func TestRefreshToken_expired(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	infra, _ := fakedApple(d)
	lib := newLib()
	refresh := usecase.NewRefreshToken(infra, lib)
	signedIn, err := usecase.NewSignInWithApple(infra, lib).Do(fixedClock(t), signInInput("apple-sub", ""))
	require.NoError(t, err)
	expired := clock.Set(t.Context(), clock.NewFixed(now.Add(token.RefreshTTL)))

	// act
	_, err = refresh.Do(expired, usecase.RefreshTokenInput{RefreshToken: signedIn.Session.RefreshToken})

	// assert
	require.ErrorIs(t, err, aerrors.ErrUnauthenticated)
	// refused without being consumed, so it still works before expiry
	_, err = refresh.Do(fixedClock(t), usecase.RefreshTokenInput{RefreshToken: signedIn.Session.RefreshToken})
	require.NoError(t, err)
}
