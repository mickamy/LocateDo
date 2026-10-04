package server_test

import (
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
	"uuid"

	"connectrpc.com/connect"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/config"
	"github.com/mickamy/LocateDo/internal/di"
	accountv1 "github.com/mickamy/LocateDo/internal/gen/locatedo/account/v1"
	"github.com/mickamy/LocateDo/internal/gen/locatedo/account/v1/accountv1connect"
	placev1 "github.com/mickamy/LocateDo/internal/gen/locatedo/place/v1"
	"github.com/mickamy/LocateDo/internal/gen/locatedo/place/v1/placev1connect"
	"github.com/mickamy/LocateDo/internal/server"
	"github.com/mickamy/LocateDo/test/tinfra"
)

const placeID = "0199a6f0-0000-7000-8000-000000000001"

func TestHandler_healthz(t *testing.T) {
	t.Parallel()

	// arrange
	srv, _ := newTestServer(t)

	// act
	req, err := http.NewRequestWithContext(t.Context(), http.MethodGet, srv.URL+"/healthz", nil)
	require.NoError(t, err)
	res, err := srv.Client().Do(req)
	require.NoError(t, err)
	defer res.Body.Close()

	// assert
	assert.Equal(t, http.StatusOK, res.StatusCode)
}

func TestHandler_auth(t *testing.T) {
	t.Parallel()

	srv, lib := newTestServer(t)
	valid := accessToken(t, lib, time.Now())
	expired := accessToken(t, lib, time.Now().Add(-2*time.Hour))

	tests := []struct {
		name          string
		authorization string
		want          connect.Code
	}{
		// The token's user belongs to no household, so the handler itself answers.
		{name: "valid token reaches the handler", authorization: "Bearer " + valid, want: connect.CodePermissionDenied},
		{name: "no header", authorization: "", want: connect.CodeUnauthenticated},
		{name: "not bearer", authorization: "Basic " + valid, want: connect.CodeUnauthenticated},
		{name: "expired", authorization: "Bearer " + expired, want: connect.CodeUnauthenticated},
		{name: "garbage", authorization: "Bearer garbage", want: connect.CodeUnauthenticated},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			// arrange
			client := placev1connect.NewPlaceServiceClient(srv.Client(), srv.URL)
			req := connect.NewRequest(&placev1.DeletePlaceRequest{Id: placeID})
			if tt.authorization != "" {
				req.Header().Set("Authorization", tt.authorization)
			}

			// act
			_, err := client.DeletePlace(t.Context(), req)

			// assert
			assert.Equal(t, tt.want, connect.CodeOf(err))
		})
	}
}

func TestHandler_publicProceduresSkipAuth(t *testing.T) {
	t.Parallel()

	// arrange
	srv, _ := newTestServer(t)
	client := accountv1connect.NewAccountServiceClient(srv.Client(), srv.URL)

	// act
	_, err := client.RefreshToken(t.Context(), connect.NewRequest(&accountv1.RefreshTokenRequest{
		RefreshToken: "never-issued",
	}))

	// assert: refused by the use case, not by the interceptor
	require.Equal(t, connect.CodeUnauthenticated, connect.CodeOf(err))
	assert.Contains(t, err.Error(), "unknown refresh token")
}

func TestHandler_validatesRequests(t *testing.T) {
	t.Parallel()

	// arrange
	srv, lib := newTestServer(t)
	client := placev1connect.NewPlaceServiceClient(srv.Client(), srv.URL)
	req := connect.NewRequest(&placev1.DeletePlaceRequest{Id: "not-a-uuid"})
	req.Header().Set("Authorization", "Bearer "+accessToken(t, lib, time.Now()))

	// act
	_, err := client.DeletePlace(t.Context(), req)

	// assert
	assert.Equal(t, connect.CodeInvalidArgument, connect.CodeOf(err))
}

func newTestServer(t *testing.T) (*httptest.Server, di.Lib) {
	t.Helper()

	cfg := di.Config{App: config.App{Env: config.EnvTest}}
	lib := di.MustNewLib(di.NewConfig())
	handlers := server.NewHandlers(tinfra.New(t), lib)
	srv := httptest.NewServer(server.Handler(cfg, lib, *handlers))
	t.Cleanup(srv.Close)
	return srv, lib
}

func accessToken(t *testing.T, lib di.Lib, now time.Time) string {
	t.Helper()

	raw, _, err := lib.Signer.IssueAccess(uuid.NewV7(), now)
	require.NoError(t, err)
	return raw
}
