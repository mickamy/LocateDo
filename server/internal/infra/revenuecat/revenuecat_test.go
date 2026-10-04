package revenuecat_test

import (
	"net/http"
	"net/http/httptest"
	"strconv"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/infra/revenuecat"
)

var now = time.Date(2026, 10, 4, 12, 0, 0, 0, time.UTC)

const (
	projectID     = "proj1234"
	entitlementID = "entl5678"
)

func millis(t time.Time) string {
	return strconv.FormatInt(t.UnixMilli(), 10)
}

func TestClient_Active(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name   string
		status int
		body   string
		want   bool
	}{
		{
			name: "active", status: http.StatusOK, want: true,
			body: `{"items":[{"entitlement_id":"entl5678","expires_at":` + millis(now.Add(time.Hour)) + `}]}`,
		},
		{
			name: "lifetime", status: http.StatusOK, want: true,
			body: `{"items":[{"entitlement_id":"entl5678","expires_at":null}]}`,
		},
		{
			name: "expired", status: http.StatusOK,
			body: `{"items":[{"entitlement_id":"entl5678","expires_at":` + millis(now.Add(-time.Hour)) + `}]}`,
		},
		{
			name: "another entitlement", status: http.StatusOK,
			body: `{"items":[{"entitlement_id":"entl0000","expires_at":null}]}`,
		},
		{name: "nothing active", status: http.StatusOK, body: `{"items":[]}`},
		{name: "unknown customer", status: http.StatusNotFound, body: `{}`},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			// arrange
			var path, authorization string
			srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				path = r.URL.EscapedPath()
				authorization = r.Header.Get("Authorization")
				w.WriteHeader(tt.status)
				_, _ = w.Write([]byte(tt.body))
			}))
			t.Cleanup(srv.Close)

			// act
			got, err := client(srv).Active(t.Context(), "user-1", now)

			// assert
			require.NoError(t, err)
			assert.Equal(t, tt.want, got)
			assert.Equal(t, "/v2/projects/proj1234/customers/user-1/active_entitlements", path)
			assert.Equal(t, "Bearer sk_test", authorization)
		})
	}
}

func TestClient_Active_failures(t *testing.T) {
	t.Parallel()

	// arrange
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusForbidden)
	}))
	t.Cleanup(srv.Close)

	// act
	_, failed := client(srv).Active(t.Context(), "user-1", now)
	_, unconfigured := revenuecat.NewClient(revenuecat.Config{BaseURL: srv.URL, APIKey: "sk_test"}, srv.Client()).
		Active(t.Context(), "user-1", now)

	// assert
	require.ErrorContains(t, failed, "403", "a key without the read permission")
	require.ErrorIs(t, unconfigured, revenuecat.ErrNotConfigured)
}

func client(srv *httptest.Server) revenuecat.Client {
	return revenuecat.NewClient(revenuecat.Config{
		BaseURL: srv.URL, APIKey: "sk_test", ProjectID: projectID, EntitlementID: entitlementID,
	}, srv.Client())
}
