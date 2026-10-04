package apple

import (
	"context"
	"crypto/ecdsa"
	"crypto/rsa"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math/big"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

const (
	DefaultBaseURL = "https://appleid.apple.com"

	issuer             = "https://appleid.apple.com"
	clientSecretTTL    = 5 * time.Minute
	keysRefetchMinWait = time.Minute
	maxResponseBytes   = 1 << 20
)

var (
	ErrInvalidToken  = errors.New("invalid apple identity token")
	ErrNotConfigured = errors.New("sign in with apple private key is not configured")
)

type Auth interface {
	VerifyIdentityToken(ctx context.Context, raw, rawNonce string, now time.Time) (Identity, error)
	ExchangeCode(ctx context.Context, code string, now time.Time) (string, error)
	Revoke(ctx context.Context, refreshToken string, now time.Time) error
}

var _ Auth = Client{}

type Config struct {
	BaseURL    string
	BundleID   string
	TeamID     string
	KeyID      string
	PrivateKey *ecdsa.PrivateKey
}

type Identity struct {
	Subject string
}

// Client talks to Sign in with Apple: it verifies identity tokens and
// exchanges and revokes the refresh tokens behind them.
type Client struct {
	cfg  Config
	http *http.Client
	keys *keySet
}

func NewClient(cfg Config, httpClient *http.Client) Client {
	return Client{cfg: cfg, http: httpClient, keys: &keySet{}}
}

type identityClaims struct {
	jwt.RegisteredClaims

	Nonce string `json:"nonce"`
}

// VerifyIdentityToken checks the token against Apple's keys and the hashed
// nonce the app passed to Apple. rawNonce is the value before hashing.
func (c Client) VerifyIdentityToken(ctx context.Context, raw, rawNonce string, now time.Time) (Identity, error) {
	var claims identityClaims
	_, err := jwt.ParseWithClaims(raw, &claims,
		func(t *jwt.Token) (any, error) {
			kid, _ := t.Header["kid"].(string)
			return c.publicKey(ctx, kid, now)
		},
		jwt.WithValidMethods([]string{jwt.SigningMethodRS256.Alg()}),
		jwt.WithIssuer(issuer),
		jwt.WithAudience(c.cfg.BundleID),
		jwt.WithExpirationRequired(),
		jwt.WithTimeFunc(func() time.Time { return now }),
	)
	if err != nil {
		return Identity{}, fmt.Errorf("%w: %w", ErrInvalidToken, err)
	}

	sum := sha256.Sum256([]byte(rawNonce))
	if claims.Nonce != hex.EncodeToString(sum[:]) {
		return Identity{}, fmt.Errorf("%w: nonce mismatch", ErrInvalidToken)
	}
	if claims.Subject == "" {
		return Identity{}, fmt.Errorf("%w: empty subject", ErrInvalidToken)
	}
	return Identity{Subject: claims.Subject}, nil
}

// ExchangeCode trades an authorization code for Apple's refresh token, which
// is what Revoke needs when the account is deleted.
func (c Client) ExchangeCode(ctx context.Context, code string, now time.Time) (string, error) {
	secret, err := c.clientSecret(now)
	if err != nil {
		return "", err
	}
	form := url.Values{
		"client_id":     {c.cfg.BundleID},
		"client_secret": {secret},
		"code":          {code},
		"grant_type":    {"authorization_code"},
	}

	var body struct {
		RefreshToken string `json:"refresh_token"`
	}
	if err := c.postForm(ctx, "/auth/token", form, &body); err != nil {
		return "", fmt.Errorf("exchange code: %w", err)
	}
	if body.RefreshToken == "" {
		return "", errors.New("exchange code: no refresh token in response")
	}
	return body.RefreshToken, nil
}

func (c Client) Revoke(ctx context.Context, refreshToken string, now time.Time) error {
	secret, err := c.clientSecret(now)
	if err != nil {
		return err
	}
	form := url.Values{
		"client_id":       {c.cfg.BundleID},
		"client_secret":   {secret},
		"token":           {refreshToken},
		"token_type_hint": {"refresh_token"},
	}
	if err := c.postForm(ctx, "/auth/revoke", form, nil); err != nil {
		return fmt.Errorf("revoke: %w", err)
	}
	return nil
}

func (c Client) clientSecret(now time.Time) (string, error) {
	if c.cfg.PrivateKey == nil {
		return "", ErrNotConfigured
	}
	claims := jwt.RegisteredClaims{
		Issuer:    c.cfg.TeamID,
		Subject:   c.cfg.BundleID,
		Audience:  jwt.ClaimStrings{issuer},
		IssuedAt:  jwt.NewNumericDate(now),
		ExpiresAt: jwt.NewNumericDate(now.Add(clientSecretTTL)),
	}
	t := jwt.NewWithClaims(jwt.SigningMethodES256, claims)
	t.Header["kid"] = c.cfg.KeyID
	signed, err := t.SignedString(c.cfg.PrivateKey)
	if err != nil {
		return "", fmt.Errorf("sign client secret: %w", err)
	}
	return signed, nil
}

func (c Client) postForm(ctx context.Context, path string, form url.Values, out any) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.cfg.BaseURL+path, strings.NewReader(form.Encode()))
	if err != nil {
		return fmt.Errorf("new request: %w", err)
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")

	res, err := c.http.Do(req)
	if err != nil {
		return fmt.Errorf("do request: %w", err)
	}
	defer res.Body.Close()

	body, err := io.ReadAll(io.LimitReader(res.Body, maxResponseBytes))
	if err != nil {
		return fmt.Errorf("read response: %w", err)
	}
	if res.StatusCode != http.StatusOK {
		var apiErr struct {
			Error string `json:"error"`
		}
		_ = json.Unmarshal(body, &apiErr)
		return fmt.Errorf("status %d: %s", res.StatusCode, apiErr.Error)
	}
	if out == nil {
		return nil
	}
	if err := json.Unmarshal(body, out); err != nil {
		return fmt.Errorf("decode response: %w", err)
	}
	return nil
}

func (c Client) publicKey(ctx context.Context, kid string, now time.Time) (*rsa.PublicKey, error) {
	if key, ok := c.keys.get(kid); ok {
		return key, nil
	}
	if !c.keys.shouldRefetch(now) {
		return nil, fmt.Errorf("unknown key id %q", kid)
	}
	if err := c.fetchKeys(ctx, now); err != nil {
		return nil, err
	}
	if key, ok := c.keys.get(kid); ok {
		return key, nil
	}
	return nil, fmt.Errorf("unknown key id %q", kid)
}

func (c Client) fetchKeys(ctx context.Context, now time.Time) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, c.cfg.BaseURL+"/auth/keys", nil)
	if err != nil {
		return fmt.Errorf("new keys request: %w", err)
	}
	res, err := c.http.Do(req)
	if err != nil {
		return fmt.Errorf("fetch keys: %w", err)
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK {
		return fmt.Errorf("fetch keys: status %d", res.StatusCode)
	}

	var set struct {
		Keys []struct {
			Kty string `json:"kty"`
			Kid string `json:"kid"`
			N   string `json:"n"`
			E   string `json:"e"`
		} `json:"keys"`
	}
	if err := json.NewDecoder(io.LimitReader(res.Body, maxResponseBytes)).Decode(&set); err != nil {
		return fmt.Errorf("decode keys: %w", err)
	}

	keys := make(map[string]*rsa.PublicKey, len(set.Keys))
	for _, k := range set.Keys {
		if k.Kty != "RSA" {
			continue
		}
		key, err := rsaKey(k.N, k.E)
		if err != nil {
			return fmt.Errorf("key %q: %w", k.Kid, err)
		}
		keys[k.Kid] = key
	}
	c.keys.replace(keys, now)
	return nil
}

func rsaKey(n, e string) (*rsa.PublicKey, error) {
	nb, err := base64.RawURLEncoding.DecodeString(n)
	if err != nil {
		return nil, fmt.Errorf("decode n: %w", err)
	}
	eb, err := base64.RawURLEncoding.DecodeString(e)
	if err != nil {
		return nil, fmt.Errorf("decode e: %w", err)
	}
	exp := new(big.Int).SetBytes(eb)
	if !exp.IsInt64() || exp.Int64() > 1<<31-1 {
		return nil, errors.New("exponent too large")
	}
	return &rsa.PublicKey{N: new(big.Int).SetBytes(nb), E: int(exp.Int64())}, nil
}

// keySet caches Apple's signing keys. An unknown kid triggers a refetch, but
// at most once per keysRefetchMinWait so bogus kids cannot hammer Apple.
type keySet struct {
	mu        sync.RWMutex
	keys      map[string]*rsa.PublicKey
	fetchedAt time.Time
}

func (s *keySet) get(kid string) (*rsa.PublicKey, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	key, ok := s.keys[kid]
	return key, ok
}

func (s *keySet) shouldRefetch(now time.Time) bool {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.fetchedAt.IsZero() || now.Sub(s.fetchedAt) >= keysRefetchMinWait
}

func (s *keySet) replace(keys map[string]*rsa.PublicKey, now time.Time) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.keys = keys
	s.fetchedAt = now
}
