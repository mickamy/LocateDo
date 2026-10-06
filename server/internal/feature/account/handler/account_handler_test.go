package handler_test

import (
	"context"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"connectrpc.com/connect"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/config"
	"github.com/mickamy/LocateDo/internal/di"
	accountv1 "github.com/mickamy/LocateDo/internal/gen/locatedo/account/v1"
	"github.com/mickamy/LocateDo/internal/gen/locatedo/account/v1/accountv1connect"
	devicev1 "github.com/mickamy/LocateDo/internal/gen/locatedo/device/v1"
	"github.com/mickamy/LocateDo/internal/infra/apple"
	"github.com/mickamy/LocateDo/internal/infra/google"
	"github.com/mickamy/LocateDo/internal/server"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestAccount_signInRefreshDelete(t *testing.T) {
	t.Parallel()

	// arrange
	client := newClient(t)

	// act & assert: sign in
	signedIn, err := client.SignInWithApple(t.Context(), connect.NewRequest(&accountv1.SignInWithAppleRequest{
		IdentityToken:     "identity:apple-sub",
		AuthorizationCode: "auth-code",
		Nonce:             "0123456789abcdef",
	}))
	require.NoError(t, err)
	session := signedIn.Msg.GetSession()
	assert.True(t, session.GetNewUser())
	assert.Nil(t, signedIn.Msg.HouseholdId)
	assert.NotEmpty(t, session.GetAccessToken())
	assert.NotEmpty(t, session.GetRefreshToken())
	assert.True(t, session.GetAccessTokenExpiresAt().AsTime().After(time.Now()))

	// act & assert: refresh
	refreshed, err := client.RefreshToken(t.Context(), connect.NewRequest(&accountv1.RefreshTokenRequest{
		RefreshToken: session.GetRefreshToken(),
	}))
	require.NoError(t, err)
	assert.Equal(t, session.GetUserId(), refreshed.Msg.GetSession().GetUserId())
	assert.False(t, refreshed.Msg.GetSession().GetNewUser())

	// act & assert: delete
	del := connect.NewRequest(&accountv1.DeleteAccountRequest{})
	del.Header().Set("Authorization", "Bearer "+refreshed.Msg.GetSession().GetAccessToken())
	_, err = client.DeleteAccount(t.Context(), del)
	require.NoError(t, err)

	_, err = client.RefreshToken(t.Context(), connect.NewRequest(&accountv1.RefreshTokenRequest{
		RefreshToken: refreshed.Msg.GetSession().GetRefreshToken(),
	}))
	assert.Equal(t, connect.CodeUnauthenticated, connect.CodeOf(err))
}

func TestAccount_SignOut_withoutAccessToken(t *testing.T) {
	t.Parallel()

	// arrange
	client := newClient(t)
	signedIn, err := client.SignInWithApple(t.Context(), connect.NewRequest(&accountv1.SignInWithAppleRequest{
		IdentityToken:     "identity:apple-sub",
		AuthorizationCode: "auth-code",
		Nonce:             "0123456789abcdef",
	}))
	require.NoError(t, err)
	refreshToken := signedIn.Msg.GetSession().GetRefreshToken()

	// act
	_, err = client.SignOut(t.Context(), connect.NewRequest(&accountv1.SignOutRequest{
		RefreshToken: refreshToken,
		Device: &accountv1.SignOutRequest_Device{
			Platform:  devicev1.Platform_PLATFORM_IOS,
			PushToken: "push-token",
		},
	}))

	// assert
	require.NoError(t, err)
	_, err = client.RefreshToken(t.Context(), connect.NewRequest(&accountv1.RefreshTokenRequest{
		RefreshToken: refreshToken,
	}))
	assert.Equal(t, connect.CodeUnauthenticated, connect.CodeOf(err))
}

func TestAccount_SignOut_unspecifiedPlatform(t *testing.T) {
	t.Parallel()

	client := newClient(t)

	_, err := client.SignOut(t.Context(), connect.NewRequest(&accountv1.SignOutRequest{
		RefreshToken: "refresh-token",
		Device:       &accountv1.SignOutRequest_Device{PushToken: "push-token"},
	}))

	assert.Equal(t, connect.CodeInvalidArgument, connect.CodeOf(err))
}

func TestAccount_SignInWithApple_invalidToken(t *testing.T) {
	t.Parallel()

	client := newClient(t)

	_, err := client.SignInWithApple(t.Context(), connect.NewRequest(&accountv1.SignInWithAppleRequest{
		IdentityToken:     "forged",
		AuthorizationCode: "auth-code",
		Nonce:             "0123456789abcdef",
	}))

	assert.Equal(t, connect.CodeUnauthenticated, connect.CodeOf(err))
}

func TestAccount_SignInWithGoogle(t *testing.T) {
	t.Parallel()

	// arrange
	client := newClient(t)

	// act
	signedIn, err := client.SignInWithGoogle(t.Context(), connect.NewRequest(&accountv1.SignInWithGoogleRequest{
		IdToken: "google:google-sub",
		Nonce:   "0123456789abcdef",
	}))

	// assert
	require.NoError(t, err)
	session := signedIn.Msg.GetSession()
	assert.True(t, session.GetNewUser())
	assert.Nil(t, signedIn.Msg.HouseholdId)
	refreshed, err := client.RefreshToken(t.Context(), connect.NewRequest(&accountv1.RefreshTokenRequest{
		RefreshToken: session.GetRefreshToken(),
	}))
	require.NoError(t, err)
	assert.Equal(t, session.GetUserId(), refreshed.Msg.GetSession().GetUserId())
}

func TestAccount_SignInWithGoogle_rejects(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		req  *accountv1.SignInWithGoogleRequest
		code connect.Code
	}{
		{
			name: "forged token",
			req:  &accountv1.SignInWithGoogleRequest{IdToken: "forged", Nonce: "0123456789abcdef"},
			code: connect.CodeUnauthenticated,
		},
		{
			name: "short nonce",
			req:  &accountv1.SignInWithGoogleRequest{IdToken: "google:sub", Nonce: "short"},
			code: connect.CodeInvalidArgument,
		},
		{
			name: "no token",
			req:  &accountv1.SignInWithGoogleRequest{Nonce: "0123456789abcdef"},
			code: connect.CodeInvalidArgument,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			client := newClient(t)

			_, err := client.SignInWithGoogle(t.Context(), connect.NewRequest(tt.req))

			assert.Equal(t, tt.code, connect.CodeOf(err))
		})
	}
}

func TestAccount_DeleteAccount_requiresToken(t *testing.T) {
	t.Parallel()

	client := newClient(t)

	_, err := client.DeleteAccount(t.Context(), connect.NewRequest(&accountv1.DeleteAccountRequest{}))

	assert.Equal(t, connect.CodeUnauthenticated, connect.CodeOf(err))
}

func TestAccount_SyncEntitlement(t *testing.T) {
	t.Parallel()

	// arrange
	client := newClient(t)
	signedIn, err := client.SignInWithApple(t.Context(), connect.NewRequest(&accountv1.SignInWithAppleRequest{
		IdentityToken:     "identity:apple-sub",
		AuthorizationCode: "auth-code",
		Nonce:             "0123456789abcdef",
	}))
	require.NoError(t, err)
	req := connect.NewRequest(&accountv1.SyncEntitlementRequest{})
	req.Header().Set("Authorization", "Bearer "+signedIn.Msg.GetSession().GetAccessToken())

	// act
	_, err = client.SyncEntitlement(t.Context(), req)

	// assert
	require.NoError(t, err)
}

func TestAccount_SyncEntitlement_requiresToken(t *testing.T) {
	t.Parallel()

	client := newClient(t)

	_, err := client.SyncEntitlement(t.Context(), connect.NewRequest(&accountv1.SyncEntitlementRequest{}))

	assert.Equal(t, connect.CodeUnauthenticated, connect.CodeOf(err))
}

func newClient(t *testing.T) accountv1connect.AccountServiceClient {
	t.Helper()

	infra := tdb.New(t).Infra()
	infra.Apple = fakeApple{}
	infra.Google = fakeGoogle{}
	lib := di.MustNewLib(di.NewConfig())
	cfg := di.Config{App: config.App{Env: config.EnvTest}}

	handlers := server.NewHandlers(cfg, infra, lib)
	srv := httptest.NewServer(server.Handler(*handlers))
	t.Cleanup(srv.Close)
	return accountv1connect.NewAccountServiceClient(srv.Client(), srv.URL)
}

// fakeApple accepts identity tokens of the form "identity:<subject>".
type fakeApple struct{}

var _ apple.Auth = fakeApple{}

func (fakeApple) VerifyIdentityToken(_ context.Context, raw, _ string, _ time.Time) (apple.Identity, error) {
	subject, ok := strings.CutPrefix(raw, "identity:")
	if !ok {
		return apple.Identity{}, apple.ErrInvalidToken
	}
	return apple.Identity{Subject: subject}, nil
}

func (fakeApple) ExchangeCode(_ context.Context, code string, _ time.Time) (string, error) {
	return "apple-refresh:" + code, nil
}

func (fakeApple) Revoke(context.Context, string, time.Time) error {
	return nil
}

// fakeGoogle accepts id tokens of the form "google:<subject>".
type fakeGoogle struct{}

var _ google.Auth = fakeGoogle{}

func (fakeGoogle) VerifyIDToken(_ context.Context, raw, _ string, _ time.Time) (google.Identity, error) {
	subject, ok := strings.CutPrefix(raw, "google:")
	if !ok {
		return google.Identity{}, google.ErrInvalidToken
	}
	return google.Identity{Subject: subject, Name: "Google User"}, nil
}
