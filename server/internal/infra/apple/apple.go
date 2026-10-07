package apple

import (
	"context"
	"crypto/ecdsa"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/golang-jwt/jwt/v5"

	"github.com/mickamy/LocateDo/internal/lib/jwks"
)

const (
	DefaultBaseURL = "https://appleid.apple.com"

	issuer           = "https://appleid.apple.com"
	clientSecretTTL  = 5 * time.Minute
	maxResponseBytes = 1 << 20
)

var (
	ErrInvalidToken            = errors.New("invalid apple identity token")
	ErrNotConfigured           = errors.New("sign in with apple private key is not configured")
	ErrServicesIDNotConfigured = errors.New("sign in with apple services id is not configured")
)

// ClientKind names the client a token was issued to: the app's Bundle ID, or
// the Services ID that web sign-in uses (the website and the Android app).
type ClientKind string

const (
	ClientApp      ClientKind = "app"
	ClientServices ClientKind = "services"
)

type Auth interface {
	VerifyIdentityToken(ctx context.Context, kind ClientKind, raw, rawNonce string, now time.Time) (Identity, error)
	ExchangeCode(ctx context.Context, kind ClientKind, code string, now time.Time) (string, error)
	Revoke(ctx context.Context, kind ClientKind, refreshToken string, now time.Time) error
}

var _ Auth = Client{}

type Config struct {
	BaseURL    string
	BundleID   string
	ServicesID string
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
	keys *jwks.Cache
}

func NewClient(cfg Config, httpClient *http.Client) Client {
	return Client{cfg: cfg, http: httpClient, keys: jwks.New(cfg.BaseURL+"/auth/keys", httpClient)}
}

type identityClaims struct {
	jwt.RegisteredClaims

	Nonce string `json:"nonce"`
}

// VerifyIdentityToken checks the token against Apple's keys, the client it
// was issued to, and the hashed nonce passed to Apple. rawNonce is the value
// before hashing.
func (c Client) VerifyIdentityToken(
	ctx context.Context, kind ClientKind, raw, rawNonce string, now time.Time,
) (Identity, error) {
	clientID, err := c.clientID(kind)
	if err != nil {
		return Identity{}, err
	}
	return c.verify(ctx, raw, rawNonce, clientID, now)
}

// ExchangeCode trades an authorization code for Apple's refresh token, which
// is what Revoke needs when the account is deleted. The code must be traded
// by the client it was issued to.
func (c Client) ExchangeCode(ctx context.Context, kind ClientKind, code string, now time.Time) (string, error) {
	clientID, err := c.clientID(kind)
	if err != nil {
		return "", err
	}
	secret, err := c.clientSecret(clientID, now)
	if err != nil {
		return "", err
	}
	form := url.Values{
		"client_id":     {clientID},
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

// Revoke must be called with the client that obtained the refresh token.
func (c Client) Revoke(ctx context.Context, kind ClientKind, refreshToken string, now time.Time) error {
	clientID, err := c.clientID(kind)
	if err != nil {
		return err
	}
	secret, err := c.clientSecret(clientID, now)
	if err != nil {
		return err
	}
	form := url.Values{
		"client_id":       {clientID},
		"client_secret":   {secret},
		"token":           {refreshToken},
		"token_type_hint": {"refresh_token"},
	}
	if err := c.postForm(ctx, "/auth/revoke", form, nil); err != nil {
		return fmt.Errorf("revoke: %w", err)
	}
	return nil
}

func (c Client) clientID(kind ClientKind) (string, error) {
	switch kind {
	case ClientApp:
		return c.cfg.BundleID, nil
	case ClientServices:
		if c.cfg.ServicesID == "" {
			return "", ErrServicesIDNotConfigured
		}
		return c.cfg.ServicesID, nil
	default:
		return "", fmt.Errorf("unknown apple client kind %q", kind)
	}
}

func (c Client) verify(ctx context.Context, raw, rawNonce, audience string, now time.Time) (Identity, error) {
	var claims identityClaims
	_, err := jwt.ParseWithClaims(raw, &claims,
		func(t *jwt.Token) (any, error) {
			kid, _ := t.Header["kid"].(string)
			return c.keys.Key(ctx, kid, now)
		},
		jwt.WithValidMethods([]string{jwt.SigningMethodRS256.Alg()}),
		jwt.WithIssuer(issuer),
		jwt.WithAudience(audience),
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

// clientSecret signs for clientID: Apple requires the secret's subject to be
// the client_id it is sent with.
func (c Client) clientSecret(clientID string, now time.Time) (string, error) {
	if c.cfg.PrivateKey == nil {
		return "", ErrNotConfigured
	}
	claims := jwt.RegisteredClaims{
		Issuer:    c.cfg.TeamID,
		Subject:   clientID,
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
