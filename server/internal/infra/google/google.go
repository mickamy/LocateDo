// Package google verifies the ID tokens Google issues to the Android app.
package google

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"slices"
	"time"

	"github.com/golang-jwt/jwt/v5"

	"github.com/mickamy/LocateDo/internal/lib/jwks"
)

const (
	DefaultBaseURL = "https://www.googleapis.com"

	keysPath = "/oauth2/v3/certs"
)

var (
	ErrInvalidToken  = errors.New("invalid google id token")
	ErrNotConfigured = errors.New("google client id is not configured")

	issuers = []string{"https://accounts.google.com", "accounts.google.com"}
)

type Auth interface {
	VerifyIDToken(ctx context.Context, raw, nonce string, now time.Time) (Identity, error)
}

var _ Auth = Client{}

type Config struct {
	BaseURL  string
	ClientID string
}

type Identity struct {
	Subject string
	Name    string
}

type Client struct {
	cfg  Config
	keys *jwks.Cache
}

func NewClient(cfg Config, httpClient *http.Client) Client {
	return Client{cfg: cfg, keys: jwks.New(cfg.BaseURL+keysPath, httpClient)}
}

type idClaims struct {
	jwt.RegisteredClaims

	Nonce string `json:"nonce"`
	Name  string `json:"name"`
}

// VerifyIDToken checks the token against Google's keys, the client id, and
// the nonce the app passed to Credential Manager, which Google carries as is.
func (c Client) VerifyIDToken(ctx context.Context, raw, nonce string, now time.Time) (Identity, error) {
	if c.cfg.ClientID == "" {
		return Identity{}, ErrNotConfigured
	}
	var claims idClaims
	_, err := jwt.ParseWithClaims(raw, &claims,
		func(t *jwt.Token) (any, error) {
			kid, _ := t.Header["kid"].(string)
			return c.keys.Key(ctx, kid, now)
		},
		jwt.WithValidMethods([]string{jwt.SigningMethodRS256.Alg()}),
		jwt.WithAudience(c.cfg.ClientID),
		jwt.WithExpirationRequired(),
		jwt.WithTimeFunc(func() time.Time { return now }),
	)
	if err != nil {
		return Identity{}, fmt.Errorf("%w: %w", ErrInvalidToken, err)
	}
	if !slices.Contains(issuers, claims.Issuer) {
		return Identity{}, fmt.Errorf("%w: issuer %q", ErrInvalidToken, claims.Issuer)
	}
	if claims.Nonce != nonce {
		return Identity{}, fmt.Errorf("%w: nonce mismatch", ErrInvalidToken)
	}
	if claims.Subject == "" {
		return Identity{}, fmt.Errorf("%w: empty subject", ErrInvalidToken)
	}
	return Identity{Subject: claims.Subject, Name: claims.Name}, nil
}
