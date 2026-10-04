package usecase_test

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/account/usecase"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestSignOut_revokesTheSession(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	infra, _ := fakedApple(d)
	lib := newLib()
	signedIn, err := usecase.NewSignInWithApple(infra, lib).Do(fixedClock(t), signInInput("apple-sub", ""))
	require.NoError(t, err)

	// act
	err = usecase.NewSignOut(infra).Do(t.Context(), usecase.SignOutInput{RefreshToken: signedIn.Session.RefreshToken})

	// assert
	require.NoError(t, err)
	_, err = usecase.NewRefreshToken(infra, lib).Do(fixedClock(t), usecase.RefreshTokenInput{
		RefreshToken: signedIn.Session.RefreshToken,
	})
	require.ErrorIs(t, err, aerrors.ErrUnauthenticated)
}

func TestSignOut_keepsOtherSessions(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	infra, _ := fakedApple(d)
	lib := newLib()
	signIn := usecase.NewSignInWithApple(infra, lib)
	phone, err := signIn.Do(fixedClock(t), signInInput("apple-sub", ""))
	require.NoError(t, err)
	tablet, err := signIn.Do(fixedClock(t), signInInput("apple-sub", ""))
	require.NoError(t, err)

	// act
	err = usecase.NewSignOut(infra).Do(t.Context(), usecase.SignOutInput{RefreshToken: phone.Session.RefreshToken})

	// assert
	require.NoError(t, err)
	_, err = usecase.NewRefreshToken(infra, lib).Do(fixedClock(t), usecase.RefreshTokenInput{
		RefreshToken: tablet.Session.RefreshToken,
	})
	require.NoError(t, err)
}

func TestSignOut_unknownToken(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)

	// act
	err := usecase.NewSignOut(d.Infra()).Do(t.Context(), usecase.SignOutInput{RefreshToken: "unknown"})

	// assert
	require.NoError(t, err)
}

func TestSignOut_deletesTheDevice(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	infra, _ := fakedApple(d)
	signedIn, err := usecase.NewSignInWithApple(infra, newLib()).Do(fixedClock(t), signInInput("apple-sub", ""))
	require.NoError(t, err)
	device := registerDevice(t, d, signedIn.Session.UserID)

	// act
	err = usecase.NewSignOut(infra).Do(t.Context(), usecase.SignOutInput{
		RefreshToken: signedIn.Session.RefreshToken,
		Device:       &usecase.SignOutDevice{Platform: device.Platform, PushToken: device.PushToken},
	})

	// assert
	require.NoError(t, err)
	assert.Equal(t, 0, countDevices(t, d, device.PushToken))
}

func TestSignOut_keepsAnotherUsersDevice(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	infra, _ := fakedApple(d)
	signedIn, err := usecase.NewSignInWithApple(infra, newLib()).Do(fixedClock(t), signInInput("apple-sub", ""))
	require.NoError(t, err)
	device := registerDevice(t, d, d.Seeder.User(t))

	// act
	err = usecase.NewSignOut(infra).Do(t.Context(), usecase.SignOutInput{
		RefreshToken: signedIn.Session.RefreshToken,
		Device:       &usecase.SignOutDevice{Platform: device.Platform, PushToken: device.PushToken},
	})

	// assert
	require.NoError(t, err)
	assert.Equal(t, 1, countDevices(t, d, device.PushToken))
}
