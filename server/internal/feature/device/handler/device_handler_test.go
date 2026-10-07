package handler_test

import (
	"net/http/httptest"
	"testing"
	"time"
	"uuid"

	"connectrpc.com/connect"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/config"
	"github.com/mickamy/LocateDo/internal/di"
	devicev1 "github.com/mickamy/LocateDo/internal/gen/locatedo/device/v1"
	"github.com/mickamy/LocateDo/internal/gen/locatedo/device/v1/devicev1connect"
	"github.com/mickamy/LocateDo/internal/server"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestDevice_registerThenTakeOver(t *testing.T) {
	t.Parallel()

	// arrange: a user without a household can register
	d := tdb.New(t)
	client := newClient(t, d)
	first := d.Seeder.User(t)
	second := d.Seeder.User(t)
	req := &devicev1.RegisterDeviceRequest{
		Platform:        devicev1.Platform_PLATFORM_IOS,
		PushToken:       "apns-token",
		ApnsEnvironment: devicev1.ApnsEnvironment_APNS_ENVIRONMENT_SANDBOX,
	}

	// act
	_, err := client.RegisterDevice(t.Context(), authed(token(t, first), req))
	require.NoError(t, err)
	_, err = client.RegisterDevice(t.Context(), authed(token(t, second), req))
	require.NoError(t, err)

	// assert
	var owner uuid.UUID
	var env string
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"SELECT user_id, apns_environment FROM devices WHERE push_token = 'apns-token'").Scan(&owner, &env))
	assert.Equal(t, second, owner, "the token follows whoever signed in last")
	assert.Equal(t, "sandbox", env)
}

func TestDevice_RegisterDevice_anonymous(t *testing.T) {
	t.Parallel()

	// arrange: a build that predates the language sends none
	d := tdb.New(t)
	client := newClient(t, d)
	req := &devicev1.RegisterDeviceRequest{
		Platform:          devicev1.Platform_PLATFORM_ANDROID,
		PushToken:         "fid",
		PromotionsConsent: true,
	}

	// act
	_, err := client.RegisterDevice(t.Context(), authed("", req))

	// assert
	require.NoError(t, err)
	var owner *uuid.UUID
	var language string
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"SELECT user_id, language FROM devices WHERE push_token = 'fid'").Scan(&owner, &language))
	assert.Nil(t, owner)
	assert.Equal(t, "en", language)
}

func TestDevice_RegisterDevice_rejects(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name    string
		arrange func(t *testing.T, d tdb.DB) (string, *devicev1.RegisterDeviceRequest)
		want    connect.Code
	}{
		{
			name: "no platform",
			arrange: func(t *testing.T, d tdb.DB) (string, *devicev1.RegisterDeviceRequest) {
				return token(t, d.Seeder.User(t)), &devicev1.RegisterDeviceRequest{PushToken: "apns-token"}
			},
			want: connect.CodeInvalidArgument,
		},
		{
			name: "empty token",
			arrange: func(t *testing.T, d tdb.DB) (string, *devicev1.RegisterDeviceRequest) {
				return token(t, d.Seeder.User(t)), &devicev1.RegisterDeviceRequest{Platform: devicev1.Platform_PLATFORM_IOS}
			},
			want: connect.CodeInvalidArgument,
		},
		{
			name: "iOS without an APNs environment",
			arrange: func(t *testing.T, d tdb.DB) (string, *devicev1.RegisterDeviceRequest) {
				return token(t, d.Seeder.User(t)), &devicev1.RegisterDeviceRequest{
					Platform:  devicev1.Platform_PLATFORM_IOS,
					PushToken: "apns-token",
				}
			},
			want: connect.CodeInvalidArgument,
		},
		{
			name: "Android with an APNs environment",
			arrange: func(t *testing.T, d tdb.DB) (string, *devicev1.RegisterDeviceRequest) {
				return token(t, d.Seeder.User(t)), &devicev1.RegisterDeviceRequest{
					Platform:        devicev1.Platform_PLATFORM_ANDROID,
					PushToken:       "fcm-token",
					ApnsEnvironment: devicev1.ApnsEnvironment_APNS_ENVIRONMENT_PRODUCTION,
				}
			},
			want: connect.CodeInvalidArgument,
		},
		{
			name: "unsupported language",
			arrange: func(t *testing.T, d tdb.DB) (string, *devicev1.RegisterDeviceRequest) {
				return token(t, d.Seeder.User(t)), &devicev1.RegisterDeviceRequest{
					Platform:        devicev1.Platform_PLATFORM_IOS,
					PushToken:       "apns-token",
					ApnsEnvironment: devicev1.ApnsEnvironment_APNS_ENVIRONMENT_PRODUCTION,
					Language:        "fr",
				}
			},
			want: connect.CodeInvalidArgument,
		},
		{
			name: "invalid token",
			arrange: func(_ *testing.T, _ tdb.DB) (string, *devicev1.RegisterDeviceRequest) {
				return "not-a-token", &devicev1.RegisterDeviceRequest{
					Platform:        devicev1.Platform_PLATFORM_IOS,
					PushToken:       "apns-token",
					ApnsEnvironment: devicev1.ApnsEnvironment_APNS_ENVIRONMENT_PRODUCTION,
				}
			},
			want: connect.CodeUnauthenticated,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			// arrange
			d := tdb.New(t)
			client := newClient(t, d)
			tok, req := tt.arrange(t, d)

			// act
			_, err := client.RegisterDevice(t.Context(), authed(tok, req))

			// assert
			assert.Equal(t, tt.want, connect.CodeOf(err))
		})
	}
}

func newClient(t *testing.T, d tdb.DB) devicev1connect.DeviceServiceClient {
	t.Helper()

	lib := di.MustNewLib(di.NewConfig())
	cfg := di.Config{App: config.App{Env: config.EnvTest}}
	handlers := server.NewHandlers(cfg, d.Infra(), lib)
	srv := httptest.NewServer(server.Handler(*handlers))
	t.Cleanup(srv.Close)
	return devicev1connect.NewDeviceServiceClient(srv.Client(), srv.URL)
}

func token(t *testing.T, userID uuid.UUID) string {
	t.Helper()

	raw, _, err := di.MustNewLib(di.NewConfig()).Signer.IssueAccess(userID, time.Now())
	require.NoError(t, err)
	return raw
}

func authed[T any](token string, msg *T) *connect.Request[T] {
	req := connect.NewRequest(msg)
	if token != "" {
		req.Header().Set("Authorization", "Bearer "+token)
	}
	return req
}
