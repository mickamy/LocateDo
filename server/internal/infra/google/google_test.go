package google_test

import (
	"crypto/rand"
	"crypto/rsa"
	"encoding/base64"
	"encoding/json"
	"math/big"
	"net/http"
	"net/http/httptest"
	"sync"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/infra/google"
)

const (
	clientID = "1234567890-abc.apps.googleusercontent.com"
	nonce    = "0123456789abcdef-nonce"
)

var now = time.Date(2026, 10, 6, 12, 0, 0, 0, time.UTC)

func TestClient_VerifyIDToken(t *testing.T) {
	t.Parallel()

	// arrange
	fake := newFakeGoogle(t)
	client := fake.client(t)

	// act
	got, err := client.VerifyIDToken(t.Context(), fake.idToken(t, validClaims()), nonce, now)

	// assert
	require.NoError(t, err)
	assert.Equal(t, google.Identity{Subject: "10769150350006150715113082367", Name: "Tetsuro"}, got)
}

func TestClient_VerifyIDToken_acceptsBothIssuerForms(t *testing.T) {
	t.Parallel()

	// arrange
	fake := newFakeGoogle(t)
	client := fake.client(t)
	claims := validClaims()
	claims.Issuer = "accounts.google.com"

	// act
	_, err := client.VerifyIDToken(t.Context(), fake.idToken(t, claims), nonce, now)

	// assert
	require.NoError(t, err)
}

func TestClient_VerifyIDToken_rejects(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name   string
		mutate func(c *testClaims)
		nonce  string
		at     time.Time
	}{
		{
			name:   "wrong audience",
			mutate: func(c *testClaims) { c.Audience = jwt.ClaimStrings{"other.apps.googleusercontent.com"} },
		},
		{name: "wrong issuer", mutate: func(c *testClaims) { c.Issuer = "https://example.com" }},
		{name: "expired", at: now.Add(2 * time.Hour)},
		{name: "nonce mismatch", nonce: "another-nonce-value"},
		{name: "empty subject", mutate: func(c *testClaims) { c.Subject = "" }},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			// arrange
			fake := newFakeGoogle(t)
			client := fake.client(t)
			claims := validClaims()
			if tt.mutate != nil {
				tt.mutate(&claims)
			}
			n := nonce
			if tt.nonce != "" {
				n = tt.nonce
			}
			at := now
			if !tt.at.IsZero() {
				at = tt.at
			}

			// act
			_, err := client.VerifyIDToken(t.Context(), fake.idToken(t, claims), n, at)

			// assert
			require.ErrorIs(t, err, google.ErrInvalidToken)
		})
	}
}

func TestClient_VerifyIDToken_signedByUnknownKey(t *testing.T) {
	t.Parallel()

	// arrange
	fake := newFakeGoogle(t)
	client := fake.client(t)
	stranger, err := rsa.GenerateKey(rand.Reader, 2048)
	require.NoError(t, err)
	tok := jwt.NewWithClaims(jwt.SigningMethodRS256, validClaims())
	tok.Header["kid"] = fake.kid
	raw, err := tok.SignedString(stranger)
	require.NoError(t, err)

	// act
	_, err = client.VerifyIDToken(t.Context(), raw, nonce, now)

	// assert
	require.ErrorIs(t, err, google.ErrInvalidToken)
}

func TestClient_VerifyIDToken_notConfigured(t *testing.T) {
	t.Parallel()

	// arrange
	fake := newFakeGoogle(t)
	client := google.NewClient(google.Config{BaseURL: fake.srv.URL}, fake.srv.Client())

	// act
	_, err := client.VerifyIDToken(t.Context(), fake.idToken(t, validClaims()), nonce, now)

	// assert
	require.ErrorIs(t, err, google.ErrNotConfigured)
	assert.Equal(t, 0, fake.keyFetches())
}

type testClaims struct {
	jwt.RegisteredClaims

	Nonce string `json:"nonce"`
	Name  string `json:"name"`
}

func validClaims() testClaims {
	return testClaims{
		Issuer:    "https://accounts.google.com",
		Subject:   "10769150350006150715113082367",
		Audience:  jwt.ClaimStrings{clientID},
		IssuedAt:  jwt.NewNumericDate(now),
		ExpiresAt: jwt.NewNumericDate(now.Add(time.Hour)),
		Nonce:     nonce,
		Name:      "Tetsuro",
	}
}

type fakeGoogle struct {
	srv *httptest.Server

	mu      sync.Mutex
	kid     string
	key     *rsa.PrivateKey
	fetches int
}

func newFakeGoogle(t *testing.T) *fakeGoogle {
	t.Helper()

	key, err := rsa.GenerateKey(rand.Reader, 2048)
	require.NoError(t, err)
	f := &fakeGoogle{key: key, kid: "kid-" + base64.RawURLEncoding.EncodeToString(key.N.Bytes()[:6])}
	mux := http.NewServeMux()
	mux.HandleFunc("GET /oauth2/v3/certs", f.serveKeys)
	f.srv = httptest.NewServer(mux)
	t.Cleanup(f.srv.Close)
	return f
}

func (f *fakeGoogle) client(t *testing.T) google.Client {
	t.Helper()

	return google.NewClient(google.Config{BaseURL: f.srv.URL, ClientID: clientID}, f.srv.Client())
}

func (f *fakeGoogle) idToken(t *testing.T, claims testClaims) string {
	t.Helper()

	tok := jwt.NewWithClaims(jwt.SigningMethodRS256, claims)
	tok.Header["kid"] = f.kid
	raw, err := tok.SignedString(f.key)
	require.NoError(t, err)
	return raw
}

func (f *fakeGoogle) keyFetches() int {
	f.mu.Lock()
	defer f.mu.Unlock()
	return f.fetches
}

func (f *fakeGoogle) serveKeys(w http.ResponseWriter, _ *http.Request) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.fetches++
	err := json.NewEncoder(w).Encode(map[string][]map[string]string{
		"keys": {{
			"kty": "RSA",
			"kid": f.kid,
			"alg": "RS256",
			"n":   base64.RawURLEncoding.EncodeToString(f.key.N.Bytes()),
			"e":   base64.RawURLEncoding.EncodeToString(big.NewInt(int64(f.key.E)).Bytes()),
		}},
	})
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
	}
}
