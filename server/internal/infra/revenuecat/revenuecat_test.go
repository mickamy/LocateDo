package revenuecat_test

import (
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/infra/revenuecat"
)

var now = time.Date(2026, 10, 4, 12, 0, 0, 0, time.UTC)

func TestClient_Active(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		body string
		want bool
	}{
		{name: "active", body: `{"subscriber":{"entitlements":{"pro":{"expires_date":"2026-11-01T00:00:00Z"}}}}`, want: true},
		{name: "lifetime", body: `{"subscriber":{"entitlements":{"pro":{"expires_date":null}}}}`, want: true},
		{name: "expired", body: `{"subscriber":{"entitlements":{"pro":{"expires_date":"2026-10-01T00:00:00Z"}}}}`},
		{name: "never bought", body: `{"subscriber":{"entitlements":{}}}`},
		{name: "another entitlement", body: `{"subscriber":{"entitlements":{"plus":{"expires_date":null}}}}`},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			// arrange
			var path, authorization string
			srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				path = r.URL.Path
				authorization = r.Header.Get("Authorization")
				_, _ = w.Write([]byte(tt.body))
			}))
			t.Cleanup(srv.Close)
			client := revenuecat.NewClient(revenuecat.Config{
				BaseURL: srv.URL, APIKey: "sk_test", Entitlement: "pro",
			}, srv.Client())

			// act
			got, err := client.Active(t.Context(), "user-1", now)

			// assert
			require.NoError(t, err)
			assert.Equal(t, tt.want, got)
			assert.Equal(t, "/v1/subscribers/user-1", path)
			assert.Equal(t, "Bearer sk_test", authorization)
		})
	}
}

func TestClient_Active_failures(t *testing.T) {
	t.Parallel()

	// arrange
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	}))
	t.Cleanup(srv.Close)

	// act
	_, failed := revenuecat.NewClient(revenuecat.Config{BaseURL: srv.URL, APIKey: "sk_test", Entitlement: "pro"},
		srv.Client()).Active(t.Context(), "user-1", now)
	_, unconfigured := revenuecat.NewClient(revenuecat.Config{BaseURL: srv.URL}, srv.Client()).
		Active(t.Context(), "user-1", now)

	// assert
	require.ErrorContains(t, failed, "500")
	require.ErrorIs(t, unconfigured, revenuecat.ErrNotConfigured)
}
