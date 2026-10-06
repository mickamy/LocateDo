package jwks_test

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

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/lib/jwks"
)

var now = time.Date(2026, 10, 3, 12, 0, 0, 0, time.UTC)

func TestCache_Key(t *testing.T) {
	t.Parallel()

	// arrange
	srv := newKeyServer(t)
	cache := jwks.New(srv.srv.URL+"/keys", srv.srv.Client())

	// act
	first, err := cache.Key(t.Context(), srv.kid(), now)
	require.NoError(t, err)
	again, err := cache.Key(t.Context(), srv.kid(), now.Add(time.Hour))

	// assert
	require.NoError(t, err)
	assert.Equal(t, srv.key().N, first.N)
	assert.Equal(t, first, again)
	assert.Equal(t, 1, srv.fetches(), "a known kid is served from the cache")
}

func TestCache_Key_unknownKidRefetchesAtMostOncePerMinute(t *testing.T) {
	t.Parallel()

	// arrange
	srv := newKeyServer(t)
	cache := jwks.New(srv.srv.URL+"/keys", srv.srv.Client())
	_, err := cache.Key(t.Context(), srv.kid(), now)
	require.NoError(t, err)
	srv.rotate(t)

	// act
	_, errSoon := cache.Key(t.Context(), srv.kid(), now.Add(10*time.Second))
	_, errLater := cache.Key(t.Context(), srv.kid(), now.Add(2*time.Minute))
	_, errBogus := cache.Key(t.Context(), "bogus", now.Add(2*time.Minute))

	// assert
	require.ErrorContains(t, errSoon, "unknown key id")
	require.NoError(t, errLater)
	require.ErrorContains(t, errBogus, "unknown key id")
	assert.Equal(t, 2, srv.fetches())
}

func TestCache_Key_skipsNonRSAKeys(t *testing.T) {
	t.Parallel()

	// arrange
	srv := newKeyServer(t)
	srv.extra = map[string]string{"kty": "EC", "kid": "ec-key", "crv": "P-256"}
	cache := jwks.New(srv.srv.URL+"/keys", srv.srv.Client())

	// act
	_, err := cache.Key(t.Context(), "ec-key", now)

	// assert
	require.ErrorContains(t, err, "unknown key id")
}

type keyServer struct {
	srv   *httptest.Server
	extra map[string]string

	mu      sync.Mutex
	current string
	keys    map[string]*rsa.PrivateKey
	count   int
}

func newKeyServer(t *testing.T) *keyServer {
	t.Helper()

	s := &keyServer{keys: map[string]*rsa.PrivateKey{}}
	s.rotate(t)
	mux := http.NewServeMux()
	mux.HandleFunc("GET /keys", s.serve)
	s.srv = httptest.NewServer(mux)
	t.Cleanup(s.srv.Close)
	return s
}

func (s *keyServer) rotate(t *testing.T) {
	t.Helper()

	key, err := rsa.GenerateKey(rand.Reader, 2048)
	require.NoError(t, err)
	s.mu.Lock()
	defer s.mu.Unlock()
	s.current = "kid-" + base64.RawURLEncoding.EncodeToString(key.N.Bytes()[:6])
	s.keys = map[string]*rsa.PrivateKey{s.current: key}
}

func (s *keyServer) kid() string {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.current
}

func (s *keyServer) key() *rsa.PrivateKey {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.keys[s.current]
}

func (s *keyServer) fetches() int {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.count
}

func (s *keyServer) serve(w http.ResponseWriter, _ *http.Request) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.count++
	var keys []map[string]string
	for kid, key := range s.keys {
		keys = append(keys, map[string]string{
			"kty": "RSA",
			"kid": kid,
			"alg": "RS256",
			"n":   base64.RawURLEncoding.EncodeToString(key.N.Bytes()),
			"e":   base64.RawURLEncoding.EncodeToString(big.NewInt(int64(key.E)).Bytes()),
		})
	}
	if s.extra != nil {
		keys = append(keys, s.extra)
	}
	if err := json.NewEncoder(w).Encode(map[string]any{"keys": keys}); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
	}
}
