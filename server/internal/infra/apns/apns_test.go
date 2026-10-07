package apns_test

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"
	"uuid"

	"github.com/golang-jwt/jwt/v5"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/infra/apns"
)

const (
	teamID = "TEAM123456"
	keyID  = "KEY1234567"
	topic  = "com.locatedo.LocateDo.dev"
)

var now = time.Date(2026, 10, 4, 12, 0, 0, 0, time.UTC)

type request struct {
	path, authorization, topic, pushType, priority, collapseID, body string
}

type fakeAPNs struct {
	srv    *httptest.Server
	key    *ecdsa.PrivateKey
	status int
	reason string

	mu       sync.Mutex
	requests []request
}

func newFakeAPNs(t *testing.T) *fakeAPNs {
	t.Helper()

	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	require.NoError(t, err)
	f := &fakeAPNs{key: key, status: http.StatusOK}
	f.srv = httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		f.mu.Lock()
		f.requests = append(f.requests, request{
			path:          r.URL.Path,
			authorization: r.Header.Get("Authorization"),
			topic:         r.Header.Get("Apns-Topic"),
			pushType:      r.Header.Get("Apns-Push-Type"),
			priority:      r.Header.Get("Apns-Priority"),
			collapseID:    r.Header.Get("Apns-Collapse-Id"),
			body:          string(body),
		})
		f.mu.Unlock()
		w.WriteHeader(f.status)
		if f.reason != "" {
			_, _ = w.Write([]byte(`{"reason":"` + f.reason + `"}`))
		}
	}))
	t.Cleanup(f.srv.Close)
	return f
}

func (f *fakeAPNs) client(key *ecdsa.PrivateKey) apns.Client {
	return apns.NewClient(apns.Config{
		ProductionURL: f.srv.URL, SandboxURL: f.srv.URL, Topic: topic, TeamID: teamID, KeyID: keyID, PrivateKey: key,
	}, f.srv.Client())
}

func TestClient_Wake(t *testing.T) {
	t.Parallel()

	// arrange
	f := newFakeAPNs(t)

	// act
	err := f.client(f.key).Wake(t.Context(), apns.Production, "abc123", now)

	// assert
	require.NoError(t, err)
	require.Len(t, f.requests, 1)
	r := f.requests[0]
	assert.Equal(t, "/3/device/abc123", r.path)
	assert.Equal(t, topic, r.topic)
	assert.Equal(t, "background", r.pushType)
	assert.Equal(t, "5", r.priority)
	assert.Empty(t, r.collapseID)
	assert.JSONEq(t, `{"aps":{"content-available":1}}`, r.body)

	raw, ok := cutBearer(r.authorization)
	require.True(t, ok)
	parsed, err := jwt.ParseWithClaims(raw, &jwt.RegisteredClaims{}, func(*jwt.Token) (any, error) {
		return &f.key.PublicKey, nil
	}, jwt.WithValidMethods([]string{"ES256"}), jwt.WithoutClaimsValidation())
	require.NoError(t, err)
	assert.Equal(t, keyID, parsed.Header["kid"])
	issuer, err := parsed.Claims.GetIssuer()
	require.NoError(t, err)
	assert.Equal(t, teamID, issuer)
}

func TestClient_Promote(t *testing.T) {
	t.Parallel()

	campaignID := uuid.MustParse("019a0000-0000-7000-8000-000000000001")
	tests := map[string]struct {
		url  string
		want string
	}{
		"with a URL": {
			url: "https://locatedo.com/news",
			want: `{"aps":{"alert":{"title":"New","body":"Try it"},"interruption-level":"passive","thread-id":"campaign"},` +
				`"campaign_id":"019a0000-0000-7000-8000-000000000001","url":"https://locatedo.com/news"}`,
		},
		"without a URL": {
			url: "",
			want: `{"aps":{"alert":{"title":"New","body":"Try it"},"interruption-level":"passive","thread-id":"campaign"},` +
				`"campaign_id":"019a0000-0000-7000-8000-000000000001"}`,
		},
	}
	for name, tt := range tests {
		t.Run(name, func(t *testing.T) {
			t.Parallel()

			// arrange
			f := newFakeAPNs(t)
			p := apns.Promotion{CampaignID: campaignID, Title: "New", Body: "Try it", URL: tt.url}

			// act
			err := f.client(f.key).Promote(t.Context(), apns.Sandbox, "abc123", p, now)

			// assert
			require.NoError(t, err)
			require.Len(t, f.requests, 1)
			r := f.requests[0]
			assert.Equal(t, "/3/device/abc123", r.path)
			assert.Equal(t, topic, r.topic)
			assert.Equal(t, "alert", r.pushType)
			assert.Equal(t, "5", r.priority)
			assert.Equal(t, campaignID.String(), r.collapseID, "a repeated push replaces the shown one")
			assert.JSONEq(t, tt.want, r.body)
		})
	}
}

func TestClient_NotifyCompletion(t *testing.T) {
	t.Parallel()

	// arrange
	f := newFakeAPNs(t)
	n := apns.CompletionNotice{Body: `Alex checked off "Milk" and 2 more`, Count: 3}

	// act
	err := f.client(f.key).NotifyCompletion(t.Context(), apns.Production, "abc123", n, now)

	// assert
	require.NoError(t, err)
	require.Len(t, f.requests, 1)
	r := f.requests[0]
	assert.Equal(t, "/3/device/abc123", r.path)
	assert.Equal(t, "alert", r.pushType)
	assert.Equal(t, "10", r.priority, "a household update is delivered at once")
	assert.Empty(t, r.collapseID)
	assert.JSONEq(t, `{"aps":{"alert":{"body":"Alex checked off \"Milk\" and 2 more"},"sound":"default",`+
		`"thread-id":"completion"},"type":"completion","count":3}`, r.body)
}

func TestClient_Promote_unregistered(t *testing.T) {
	t.Parallel()

	// arrange
	f := newFakeAPNs(t)
	f.status = http.StatusGone
	f.reason = "Unregistered"

	// act
	err := f.client(f.key).Promote(t.Context(), apns.Production, "abc123", apns.Promotion{Title: "t", Body: "b"}, now)

	// assert
	require.ErrorIs(t, err, apns.ErrUnregistered)
}

func TestClient_Wake_picksTheHostByEnvironment(t *testing.T) {
	t.Parallel()

	// arrange
	production := newFakeAPNs(t)
	sandbox := newFakeAPNs(t)
	c := apns.NewClient(apns.Config{
		ProductionURL: production.srv.URL, SandboxURL: sandbox.srv.URL,
		Topic: topic, TeamID: teamID, KeyID: keyID, PrivateKey: production.key,
	}, production.srv.Client())

	// act
	require.NoError(t, c.Wake(t.Context(), apns.Production, "release-build", now))
	require.NoError(t, c.Wake(t.Context(), apns.Sandbox, "xcode-build", now))

	// assert
	require.Len(t, production.requests, 1)
	require.Len(t, sandbox.requests, 1)
	assert.Equal(t, "/3/device/release-build", production.requests[0].path)
	assert.Equal(t, "/3/device/xcode-build", sandbox.requests[0].path)
}

func TestClient_Wake_unknownEnvironment(t *testing.T) {
	t.Parallel()

	// arrange
	f := newFakeAPNs(t)

	// act
	err := f.client(f.key).Wake(t.Context(), "", "abc123", now)

	// assert
	require.ErrorContains(t, err, "unknown APNs environment")
	assert.Empty(t, f.requests)
}

func TestClient_Wake_reusesTheProviderTokenWithinItsLifetime(t *testing.T) {
	t.Parallel()

	// arrange
	f := newFakeAPNs(t)
	c := f.client(f.key)

	// act
	require.NoError(t, c.Wake(t.Context(), apns.Production, "a", now))
	require.NoError(t, c.Wake(t.Context(), apns.Production, "b", now.Add(30*time.Minute)))
	require.NoError(t, c.Wake(t.Context(), apns.Production, "c", now.Add(55*time.Minute)))

	// assert
	assert.Equal(t, f.requests[0].authorization, f.requests[1].authorization)
	assert.NotEqual(t, f.requests[1].authorization, f.requests[2].authorization, "renewed before Apple's one-hour limit")
}

func TestClient_Wake_errors(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name         string
		status       int
		reason       string
		unregistered bool
	}{
		{name: "unregistered", status: http.StatusGone, reason: "Unregistered", unregistered: true},
		{name: "bad device token", status: http.StatusBadRequest, reason: "BadDeviceToken", unregistered: true},
		{name: "server trouble", status: http.StatusServiceUnavailable, reason: "ServiceUnavailable"},
		{name: "wrong topic", status: http.StatusBadRequest, reason: "DeviceTokenNotForTopic"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			// arrange
			f := newFakeAPNs(t)
			f.status = tt.status
			f.reason = tt.reason

			// act
			err := f.client(f.key).Wake(t.Context(), apns.Production, "abc123", now)

			// assert
			require.Error(t, err)
			assert.Equal(t, tt.unregistered, errorIsUnregistered(err))
			require.ErrorIs(t, err, apns.ErrNotDelivered, "APNs answered, so the push surely did not go out")
			assert.Contains(t, err.Error(), tt.reason)
		})
	}
}

func TestClient_Wake_connectionLostLeavesTheOutcomeUnknown(t *testing.T) {
	t.Parallel()

	// arrange
	f := newFakeAPNs(t)
	c := f.client(f.key)
	f.srv.Close()

	// act
	err := c.Wake(t.Context(), apns.Production, "abc123", now)

	// assert
	require.Error(t, err)
	assert.NotErrorIs(t, err, apns.ErrNotDelivered)
}

func TestClient_Wake_notConfigured(t *testing.T) {
	t.Parallel()

	// arrange
	f := newFakeAPNs(t)

	// act
	err := f.client(nil).Wake(t.Context(), apns.Production, "abc123", now)

	// assert
	require.NoError(t, err)
	assert.Empty(t, f.requests, "nothing is sent without a key")
}

func cutBearer(header string) (string, bool) {
	return strings.CutPrefix(header, "bearer ")
}

func errorIsUnregistered(err error) bool {
	return errors.Is(err, apns.ErrUnregistered)
}
