package apple_test

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"encoding/pem"
	"math/big"
	"net/http"
	"net/http/httptest"
	"sync"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/infra/apple"
)

const (
	bundleID = "com.locatedo.LocateDo"
	teamID   = "TEAM123456"
	keyID    = "KEY1234567"
	rawNonce = "0123456789abcdef-raw-nonce"
)

var now = time.Date(2026, 10, 3, 12, 0, 0, 0, time.UTC)

func TestClient_VerifyIdentityToken(t *testing.T) {
	t.Parallel()

	// arrange
	fake := newFakeApple(t)
	client := fake.client(t)

	// act
	got, err := client.VerifyIdentityToken(t.Context(), fake.identityToken(t, validClaims()), rawNonce, now)

	// assert
	require.NoError(t, err)
	assert.Equal(t, "001234.abcd.5678", got.Subject)
}

func TestClient_VerifyIdentityToken_rejects(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name   string
		mutate func(c *testClaims)
		nonce  string
		at     time.Time
	}{
		{name: "wrong audience", mutate: func(c *testClaims) { c.Audience = jwt.ClaimStrings{"com.example.other"} }},
		{name: "wrong issuer", mutate: func(c *testClaims) { c.Issuer = "https://example.com" }},
		{name: "expired", at: now.Add(2 * time.Hour)},
		{name: "nonce mismatch", nonce: "another-raw-nonce-value"},
		{name: "empty subject", mutate: func(c *testClaims) { c.Subject = "" }},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			// arrange
			fake := newFakeApple(t)
			client := fake.client(t)
			claims := validClaims()
			if tt.mutate != nil {
				tt.mutate(&claims)
			}
			nonce := rawNonce
			if tt.nonce != "" {
				nonce = tt.nonce
			}
			at := now
			if !tt.at.IsZero() {
				at = tt.at
			}

			// act
			_, err := client.VerifyIdentityToken(t.Context(), fake.identityToken(t, claims), nonce, at)

			// assert
			require.ErrorIs(t, err, apple.ErrInvalidToken)
		})
	}
}

func TestClient_VerifyIdentityToken_signedByUnknownKey(t *testing.T) {
	t.Parallel()

	// arrange
	fake := newFakeApple(t)
	client := fake.client(t)
	stranger, err := rsa.GenerateKey(rand.Reader, 2048)
	require.NoError(t, err)
	tok := jwt.NewWithClaims(jwt.SigningMethodRS256, validClaims())
	tok.Header["kid"] = fake.kid
	raw, err := tok.SignedString(stranger)
	require.NoError(t, err)

	// act
	_, err = client.VerifyIdentityToken(t.Context(), raw, rawNonce, now)

	// assert
	require.ErrorIs(t, err, apple.ErrInvalidToken)
}

func TestClient_VerifyIdentityToken_keyRotation(t *testing.T) {
	t.Parallel()

	// arrange
	fake := newFakeApple(t)
	client := fake.client(t)
	_, err := client.VerifyIdentityToken(t.Context(), fake.identityToken(t, validClaims()), rawNonce, now)
	require.NoError(t, err)
	fake.rotate(t)

	// act: a new kid within the refetch window is not fetched yet
	rotated := fake.identityToken(t, validClaims())
	_, errSoon := client.VerifyIdentityToken(t.Context(), rotated, rawNonce, now.Add(10*time.Second))
	_, errLater := client.VerifyIdentityToken(t.Context(), rotated, rawNonce, now.Add(2*time.Minute))

	// assert
	require.ErrorIs(t, errSoon, apple.ErrInvalidToken)
	require.NoError(t, errLater)
	assert.Equal(t, 2, fake.keyFetches())
}

func TestClient_ExchangeCode(t *testing.T) {
	t.Parallel()

	// arrange
	fake := newFakeApple(t)
	client := fake.client(t)

	// act
	got, err := client.ExchangeCode(t.Context(), "auth-code", now)

	// assert
	require.NoError(t, err)
	assert.Equal(t, "apple-refresh-token", got)
	req := fake.lastForm("/auth/token")
	assert.Equal(t, "auth-code", req.Get("code"))
	assert.Equal(t, "authorization_code", req.Get("grant_type"))
	assert.Equal(t, bundleID, req.Get("client_id"))
	fake.assertClientSecret(t, req.Get("client_secret"))
}

func TestClient_ExchangeCode_rejected(t *testing.T) {
	t.Parallel()

	// arrange
	fake := newFakeApple(t)
	client := fake.client(t)

	// act
	_, err := client.ExchangeCode(t.Context(), "bad-code", now)

	// assert
	require.ErrorContains(t, err, "invalid_grant")
}

func TestClient_Revoke(t *testing.T) {
	t.Parallel()

	// arrange
	fake := newFakeApple(t)
	client := fake.client(t)

	// act
	err := client.Revoke(t.Context(), "apple-refresh-token", now)

	// assert
	require.NoError(t, err)
	req := fake.lastForm("/auth/revoke")
	assert.Equal(t, "apple-refresh-token", req.Get("token"))
	assert.Equal(t, "refresh_token", req.Get("token_type_hint"))
	fake.assertClientSecret(t, req.Get("client_secret"))
}

func TestParsePrivateKey(t *testing.T) {
	t.Parallel()

	// arrange
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	require.NoError(t, err)
	der, err := x509.MarshalPKCS8PrivateKey(key)
	require.NoError(t, err)
	p8 := pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: der})

	// act
	got, err := apple.ParsePrivateKey(p8)

	// assert
	require.NoError(t, err)
	assert.True(t, key.Equal(got))

	_, err = apple.ParsePrivateKey([]byte("not a pem"))
	require.Error(t, err)
}

type testClaims struct {
	jwt.RegisteredClaims

	Nonce string `json:"nonce"`
}

func validClaims() testClaims {
	sum := sha256.Sum256([]byte(rawNonce))
	return testClaims{
		Issuer:    "https://appleid.apple.com",
		Subject:   "001234.abcd.5678",
		Audience:  jwt.ClaimStrings{bundleID},
		IssuedAt:  jwt.NewNumericDate(now),
		ExpiresAt: jwt.NewNumericDate(now.Add(time.Hour)),
		Nonce:     hex.EncodeToString(sum[:]),
	}
}

type fakeApple struct {
	srv    *httptest.Server
	secret *ecdsa.PrivateKey

	mu      sync.Mutex
	kid     string
	key     *rsa.PrivateKey
	fetches int
	forms   map[string]map[string][]string
}

func newFakeApple(t *testing.T) *fakeApple {
	t.Helper()

	secret, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	require.NoError(t, err)
	f := &fakeApple{secret: secret, forms: map[string]map[string][]string{}}
	f.rotate(t)

	mux := http.NewServeMux()
	mux.HandleFunc("GET /auth/keys", f.serveKeys)
	mux.HandleFunc("POST /auth/token", f.serveToken)
	mux.HandleFunc("POST /auth/revoke", f.serveRevoke)
	f.srv = httptest.NewServer(mux)
	t.Cleanup(f.srv.Close)
	return f
}

func (f *fakeApple) client(t *testing.T) apple.Client {
	t.Helper()

	return apple.NewClient(apple.Config{
		BaseURL:    f.srv.URL,
		BundleID:   bundleID,
		TeamID:     teamID,
		KeyID:      keyID,
		PrivateKey: f.secret,
	}, f.srv.Client())
}

func (f *fakeApple) rotate(t *testing.T) {
	t.Helper()

	key, err := rsa.GenerateKey(rand.Reader, 2048)
	require.NoError(t, err)
	f.mu.Lock()
	defer f.mu.Unlock()
	f.key = key
	f.kid = "kid-" + base64.RawURLEncoding.EncodeToString(key.N.Bytes()[:6])
}

func (f *fakeApple) identityToken(t *testing.T, claims testClaims) string {
	t.Helper()

	f.mu.Lock()
	defer f.mu.Unlock()
	tok := jwt.NewWithClaims(jwt.SigningMethodRS256, claims)
	tok.Header["kid"] = f.kid
	raw, err := tok.SignedString(f.key)
	require.NoError(t, err)
	return raw
}

func (f *fakeApple) keyFetches() int {
	f.mu.Lock()
	defer f.mu.Unlock()
	return f.fetches
}

func (f *fakeApple) lastForm(path string) formValues {
	f.mu.Lock()
	defer f.mu.Unlock()
	return formValues(f.forms[path])
}

func (f *fakeApple) assertClientSecret(t *testing.T, raw string) {
	t.Helper()

	var claims jwt.RegisteredClaims
	tok, err := jwt.ParseWithClaims(raw, &claims,
		func(*jwt.Token) (any, error) { return &f.secret.PublicKey, nil },
		jwt.WithValidMethods([]string{"ES256"}),
		jwt.WithTimeFunc(func() time.Time { return now }),
	)
	require.NoError(t, err)
	assert.Equal(t, keyID, tok.Header["kid"])
	assert.Equal(t, teamID, claims.Issuer)
	assert.Equal(t, bundleID, claims.Subject)
	assert.Equal(t, jwt.ClaimStrings{"https://appleid.apple.com"}, claims.Audience)
}

func (f *fakeApple) serveKeys(w http.ResponseWriter, _ *http.Request) {
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

func (f *fakeApple) serveToken(w http.ResponseWriter, r *http.Request) {
	f.record(r)
	if r.PostForm.Get("code") != "auth-code" {
		w.WriteHeader(http.StatusBadRequest)
		_, _ = w.Write([]byte(`{"error":"invalid_grant"}`))
		return
	}
	_, _ = w.Write([]byte(`{"access_token":"a","refresh_token":"apple-refresh-token","id_token":"i"}`))
}

func (f *fakeApple) serveRevoke(_ http.ResponseWriter, r *http.Request) {
	f.record(r)
}

func (f *fakeApple) record(r *http.Request) {
	_ = r.ParseForm()
	f.mu.Lock()
	defer f.mu.Unlock()
	f.forms[r.URL.Path] = r.PostForm
}

type formValues map[string][]string

func (v formValues) Get(key string) string {
	if len(v[key]) == 0 {
		return ""
	}
	return v[key][0]
}
