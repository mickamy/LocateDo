package webhook_test

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/config"
	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/server/webhook"
	"github.com/mickamy/LocateDo/test/tdb"
)

const secret = "Bearer whsec" //nolint:gosec // a test value, not a credential

func TestRevenueCat_queuesAnEntitlementCheckPerUser(t *testing.T) {
	t.Parallel()

	// arrange: a transfer names two of our users and one anonymous id
	d := tdb.New(t)
	from, to := uuid.NewV7(), uuid.NewV7()
	body := `{"event":{"type":"TRANSFER","transferred_from":["$RCAnonymousID:abc","` + from.String() +
		`"],"transferred_to":["` + to.String() + `"]}}`

	// act
	res := post(t, d, secret, body)
	again := post(t, d, secret, body)

	// assert
	assert.Equal(t, http.StatusOK, res.Code)
	assert.Equal(t, http.StatusOK, again.Code)
	assert.ElementsMatch(t, []uuid.UUID{from, to}, queuedUsers(t, d), "one pending check per user, even when retried")
}

func TestRevenueCat_answers(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name          string
		authorization string
		body          string
		want          int
		queued        bool
	}{
		{name: "purchase", authorization: secret, want: http.StatusOK, queued: true,
			body: `{"event":{"type":"INITIAL_PURCHASE","app_user_id":"` + uuid.NewV7().String() + `"}}`},
		{name: "test event", authorization: secret, want: http.StatusOK,
			body: `{"event":{"type":"TEST","app_user_id":"` + uuid.NewV7().String() + `"}}`},
		{name: "anonymous user", authorization: secret, want: http.StatusOK,
			body: `{"event":{"type":"INITIAL_PURCHASE","app_user_id":"$RCAnonymousID:abc"}}`},
		{name: "wrong secret", authorization: "Bearer nope", want: http.StatusUnauthorized,
			body: `{"event":{"type":"INITIAL_PURCHASE","app_user_id":"` + uuid.NewV7().String() + `"}}`},
		{name: "malformed", authorization: secret, want: http.StatusBadRequest, body: `{`},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			// arrange
			d := tdb.New(t)

			// act
			res := post(t, d, tt.authorization, tt.body)

			// assert
			assert.Equal(t, tt.want, res.Code)
			assert.Equal(t, tt.queued, len(queuedUsers(t, d)) == 1)
		})
	}
}

func TestRevenueCat_refusesWithoutAConfiguredSecret(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	h := webhook.NewRevenueCat(di.Config{}, d.Infra())
	req := httptest.NewRequestWithContext(t.Context(), http.MethodPost, "/webhooks/revenuecat",
		strings.NewReader(`{"event":{"type":"INITIAL_PURCHASE","app_user_id":"`+uuid.NewV7().String()+`"}}`))
	rec := httptest.NewRecorder()

	// act
	h.ServeHTTP(rec, req)

	// assert
	assert.Equal(t, http.StatusUnauthorized, rec.Code)
}

func post(t *testing.T, d tdb.DB, authorization, body string) *httptest.ResponseRecorder {
	t.Helper()

	h := webhook.NewRevenueCat(di.Config{RevenueCat: config.RevenueCat{WebhookAuth: secret}}, d.Infra())
	req := httptest.NewRequestWithContext(t.Context(), http.MethodPost, "/webhooks/revenuecat", strings.NewReader(body))
	req.Header.Set("Authorization", authorization)
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	return rec
}

func queuedUsers(t *testing.T, d tdb.DB) []uuid.UUID {
	t.Helper()

	rows, err := d.Writer.Query(t.Context(),
		"SELECT (payload->>'user_id')::uuid FROM outbox_messages WHERE kind = 'sync_entitlement'")
	require.NoError(t, err)
	defer rows.Close()
	var out []uuid.UUID
	for rows.Next() {
		var id uuid.UUID
		require.NoError(t, rows.Scan(&id))
		out = append(out, id)
	}
	require.NoError(t, rows.Err())
	return out
}
