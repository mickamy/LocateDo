package usecase_test

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/account/usecase"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/internal/lib/token"
)

func TestRefreshToken_rotates(t *testing.T) {
	t.Parallel()

	// arrange
	e := newEnv(t)
	signedIn, err := e.signIn.Do(e.ctx, signInInput("apple-sub", ""))
	require.NoError(t, err)
	later := clock.Set(t.Context(), clock.NewFixed(now.Add(2*time.Hour)))

	// act
	out, err := e.refresh.Do(later, usecase.RefreshTokenInput{RefreshToken: signedIn.Session.RefreshToken})

	// assert
	require.NoError(t, err)
	assert.Equal(t, signedIn.Session.UserID, out.Session.UserID)
	assert.False(t, out.Session.NewUser)
	assert.NotEqual(t, signedIn.Session.RefreshToken, out.Session.RefreshToken)
	assert.Equal(t, now.Add(2*time.Hour+token.AccessTTL), out.Session.AccessTokenExpiresAt)
}

func TestRefreshToken_reuseRevokesFamily(t *testing.T) {
	t.Parallel()

	// arrange
	e := newEnv(t)
	signedIn, err := e.signIn.Do(e.ctx, signInInput("apple-sub", ""))
	require.NoError(t, err)
	stolen := signedIn.Session.RefreshToken
	rotated, err := e.refresh.Do(e.ctx, usecase.RefreshTokenInput{RefreshToken: stolen})
	require.NoError(t, err)

	// act
	_, reuseErr := e.refresh.Do(e.ctx, usecase.RefreshTokenInput{RefreshToken: stolen})
	_, rotatedErr := e.refresh.Do(e.ctx, usecase.RefreshTokenInput{RefreshToken: rotated.Session.RefreshToken})

	// assert
	require.ErrorIs(t, reuseErr, aerrors.ErrUnauthenticated)
	require.ErrorContains(t, reuseErr, "reused")
	require.ErrorIs(t, rotatedErr, aerrors.ErrUnauthenticated)
}

func TestRefreshToken_reuseKeepsOtherFamilies(t *testing.T) {
	t.Parallel()

	// arrange: the same user signed in on two devices
	e := newEnv(t)
	phone, err := e.signIn.Do(e.ctx, signInInput("apple-sub", ""))
	require.NoError(t, err)
	tablet, err := e.signIn.Do(e.ctx, signInInput("apple-sub", ""))
	require.NoError(t, err)
	_, err = e.refresh.Do(e.ctx, usecase.RefreshTokenInput{RefreshToken: phone.Session.RefreshToken})
	require.NoError(t, err)

	// act
	_, reuseErr := e.refresh.Do(e.ctx, usecase.RefreshTokenInput{RefreshToken: phone.Session.RefreshToken})
	_, tabletErr := e.refresh.Do(e.ctx, usecase.RefreshTokenInput{RefreshToken: tablet.Session.RefreshToken})

	// assert
	require.ErrorIs(t, reuseErr, aerrors.ErrUnauthenticated)
	require.NoError(t, tabletErr)
}

func TestRefreshToken_unknown(t *testing.T) {
	t.Parallel()

	e := newEnv(t)

	_, err := e.refresh.Do(e.ctx, usecase.RefreshTokenInput{RefreshToken: "never-issued"})

	require.ErrorIs(t, err, aerrors.ErrUnauthenticated)
}

func TestRefreshToken_expired(t *testing.T) {
	t.Parallel()

	// arrange
	e := newEnv(t)
	signedIn, err := e.signIn.Do(e.ctx, signInInput("apple-sub", ""))
	require.NoError(t, err)
	expired := clock.Set(t.Context(), clock.NewFixed(now.Add(token.RefreshTTL)))

	// act
	_, err = e.refresh.Do(expired, usecase.RefreshTokenInput{RefreshToken: signedIn.Session.RefreshToken})

	// assert
	require.ErrorIs(t, err, aerrors.ErrUnauthenticated)
	// refused without being consumed, so it still works before expiry
	_, err = e.refresh.Do(e.ctx, usecase.RefreshTokenInput{RefreshToken: signedIn.Session.RefreshToken})
	require.NoError(t, err)
}
