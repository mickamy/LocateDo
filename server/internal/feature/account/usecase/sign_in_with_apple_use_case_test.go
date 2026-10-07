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
	"github.com/mickamy/LocateDo/internal/infra/apple"
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
	assert.Nil(t, out.HouseholdID)
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

func TestSignInWithApple_servicesID(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	infra, fake := fakedApple(d)
	lib := newLib()

	// act
	out, err := usecase.NewSignInWithApple(infra, lib).Do(fixedClock(t), servicesSignInInput("apple-sub", "Tetsuro"))

	// assert
	require.NoError(t, err)
	assert.True(t, out.Session.NewUser)
	assert.Equal(t, []apple.ClientKind{apple.ClientServices}, fake.exchangedClients())
	appleToken, err := repository.NewAppleToken(d.Reader).Find(t.Context(), out.Session.UserID)
	require.NoError(t, err)
	assert.Equal(t, apple.ClientServices, appleToken.Client)
	assert.Equal(t, "apple-refresh:auth-code", openAppleToken(t, d, lib, out.Session.UserID))
}

func TestSignInWithApple_servicesIDReachesTheAppAccount(t *testing.T) {
	t.Parallel()

	// arrange: the same Apple ID signed in on iPhone first
	d := tdb.New(t)
	infra, _ := fakedApple(d)
	signIn := usecase.NewSignInWithApple(infra, newLib())
	onIPhone, err := signIn.Do(fixedClock(t), signInInput("apple-sub", "Tetsuro"))
	require.NoError(t, err)

	// act
	onAndroid, err := signIn.Do(fixedClock(t), servicesSignInInput("apple-sub", ""))

	// assert
	require.NoError(t, err)
	assert.False(t, onAndroid.Session.NewUser)
	assert.Equal(t, onIPhone.Session.UserID, onAndroid.Session.UserID)
	appleToken, err := repository.NewAppleToken(d.Reader).Find(t.Context(), onIPhone.Session.UserID)
	require.NoError(t, err)
	assert.Equal(t, apple.ClientServices, appleToken.Client, "the latest token is the one revoked on deletion")
}

func TestSignInWithApple_tokenOfAnotherClient(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		in   usecase.SignInWithAppleInput
	}{
		{name: "app token sent as services", in: func() usecase.SignInWithAppleInput {
			in := signInInput("apple-sub", "")
			in.Client = apple.ClientServices
			return in
		}()},
		{name: "services token sent as app", in: func() usecase.SignInWithAppleInput {
			in := servicesSignInInput("apple-sub", "")
			in.Client = apple.ClientApp
			return in
		}()},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			// arrange
			d := tdb.New(t)
			infra, fake := fakedApple(d)

			// act
			_, err := usecase.NewSignInWithApple(infra, newLib()).Do(fixedClock(t), tt.in)

			// assert
			require.ErrorIs(t, err, aerrors.ErrUnauthenticated)
			assert.Empty(t, fake.exchangedClients())
		})
	}
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

func TestSignInWithApple_returnsHousehold(t *testing.T) {
	t.Parallel()

	// arrange: signed in once on another device and joined a household there
	d := tdb.New(t)
	infra, _ := fakedApple(d)
	signIn := usecase.NewSignInWithApple(infra, newLib())
	first, err := signIn.Do(fixedClock(t), signInInput("apple-sub", ""))
	require.NoError(t, err)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	d.Seeder.Join(t, h.ID, first.Session.UserID)

	// act
	out, err := signIn.Do(fixedClock(t), signInInput("apple-sub", ""))

	// assert
	require.NoError(t, err)
	require.NotNil(t, out.HouseholdID)
	assert.Equal(t, h.ID, *out.HouseholdID)
}
