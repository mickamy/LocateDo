package server_test

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"connectrpc.com/connect"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/config"
	"github.com/mickamy/LocateDo/internal/di"
	placev1 "github.com/mickamy/LocateDo/internal/gen/locatedo/place/v1"
	"github.com/mickamy/LocateDo/internal/gen/locatedo/place/v1/placev1connect"
	"github.com/mickamy/LocateDo/internal/server"
	"github.com/mickamy/LocateDo/test/tinfra"
)

func TestHandler_healthz(t *testing.T) {
	t.Parallel()

	// arrange
	srv := newTestServer(t)

	// act
	req, err := http.NewRequestWithContext(t.Context(), http.MethodGet, srv.URL+"/healthz", nil)
	require.NoError(t, err)
	res, err := srv.Client().Do(req)
	require.NoError(t, err)
	defer res.Body.Close()

	// assert
	assert.Equal(t, http.StatusOK, res.StatusCode)
}

func TestHandler_unimplemented(t *testing.T) {
	t.Parallel()

	// arrange
	srv := newTestServer(t)
	client := placev1connect.NewPlaceServiceClient(srv.Client(), srv.URL)

	// act
	_, err := client.DeletePlace(t.Context(), connect.NewRequest(&placev1.DeletePlaceRequest{
		Id: "0199a6f0-0000-7000-8000-000000000001",
	}))

	// assert
	assert.Equal(t, connect.CodeUnimplemented, connect.CodeOf(err))
}

func TestHandler_validatesRequests(t *testing.T) {
	t.Parallel()

	// arrange
	srv := newTestServer(t)
	client := placev1connect.NewPlaceServiceClient(srv.Client(), srv.URL)

	// act
	_, err := client.DeletePlace(t.Context(), connect.NewRequest(&placev1.DeletePlaceRequest{
		Id: "not-a-uuid",
	}))

	// assert
	assert.Equal(t, connect.CodeInvalidArgument, connect.CodeOf(err))
}

func newTestServer(t *testing.T) *httptest.Server {
	t.Helper()

	cfg := di.Config{App: config.App{Env: config.EnvTest}}
	handlers := server.NewHandlers(tinfra.New(t))
	srv := httptest.NewServer(server.Handler(cfg, *handlers))
	t.Cleanup(srv.Close)
	return srv
}
