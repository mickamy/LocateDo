// Package fcm sends silent data messages through Firebase Cloud Messaging.
package fcm

import (
	"bytes"
	"context"
	"crypto/rsa"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"encoding/pem"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"

	"github.com/golang-jwt/jwt/v5"

	"github.com/mickamy/LocateDo/internal/lib/logger"
)

const (
	DefaultBaseURL  = "https://fcm.googleapis.com"
	DefaultTokenURL = "https://oauth2.googleapis.com/token" //nolint:gosec // a URL, not a credential

	scope            = "https://www.googleapis.com/auth/firebase.messaging"
	assertionTTL     = time.Hour
	tokenMargin      = 5 * time.Minute
	maxResponseBytes = 1 << 16
)

// ErrUnregistered means the installation is no longer registered with FCM;
// the caller should forget it.
var ErrUnregistered = errors.New("fcm: installation is no longer registered")

type Pusher interface {
	// Wake sends the installation a data message that lets the app pull in
	// the background.
	Wake(ctx context.Context, installationID string, now time.Time) error
}

var _ Pusher = Client{}

type ServiceAccount struct {
	ProjectID   string
	ClientEmail string
	PrivateKey  *rsa.PrivateKey
}

// ParseServiceAccount reads the JSON key Firebase issues for a service
// account, given as the file or as base64 of it, the one-line form that fits
// in a .env file.
func ParseServiceAccount(raw string) (ServiceAccount, error) {
	raw = strings.TrimSpace(raw)
	data := []byte(raw)
	if !strings.HasPrefix(raw, "{") {
		decoded, err := base64.StdEncoding.DecodeString(raw)
		if err != nil {
			return ServiceAccount{}, fmt.Errorf("decode base64: %w", err)
		}
		data = decoded
	}

	var file struct {
		ProjectID   string `json:"project_id"`
		ClientEmail string `json:"client_email"`
		PrivateKey  string `json:"private_key"`
	}
	if err := json.Unmarshal(data, &file); err != nil {
		return ServiceAccount{}, fmt.Errorf("decode service account: %w", err)
	}
	if file.ProjectID == "" || file.ClientEmail == "" {
		return ServiceAccount{}, errors.New("service account: project_id and client_email are required")
	}
	block, _ := pem.Decode([]byte(file.PrivateKey))
	if block == nil {
		return ServiceAccount{}, errors.New("service account: no PEM block in private_key")
	}
	key, err := x509.ParsePKCS8PrivateKey(block.Bytes)
	if err != nil {
		return ServiceAccount{}, fmt.Errorf("parse private_key: %w", err)
	}
	rsaKey, ok := key.(*rsa.PrivateKey)
	if !ok {
		return ServiceAccount{}, fmt.Errorf("parse private_key: want RSA key, got %T", key)
	}
	return ServiceAccount{ProjectID: file.ProjectID, ClientEmail: file.ClientEmail, PrivateKey: rsaKey}, nil
}

type Config struct {
	BaseURL        string
	TokenURL       string
	ServiceAccount *ServiceAccount
}

// Client sends data messages with the FCM HTTP v1 API, authenticating as the
// service account through a self-signed JWT exchanged for an access token.
type Client struct {
	cfg   Config
	http  *http.Client
	token *accessToken
}

func NewClient(cfg Config, httpClient *http.Client) Client {
	return Client{cfg: cfg, http: httpClient, token: &accessToken{}}
}

func (c Client) Wake(ctx context.Context, installationID string, now time.Time) error {
	account := c.cfg.ServiceAccount
	if account == nil {
		logger.Debug(ctx, "fcm is not configured; dropping push", "installation_id", installationID)
		return nil
	}
	bearer, err := c.token.get(ctx, c, now)
	if err != nil {
		return err
	}

	body, err := json.Marshal(map[string]any{
		"message": map[string]any{
			"fid":     installationID,
			"data":    map[string]string{"reason": "sync"},
			"android": map[string]string{"priority": "normal"},
		},
	})
	if err != nil {
		return fmt.Errorf("encode message: %w", err)
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost,
		c.cfg.BaseURL+"/v1/projects/"+account.ProjectID+"/messages:send", bytes.NewReader(body))
	if err != nil {
		return fmt.Errorf("new request: %w", err)
	}
	req.Header.Set("Authorization", "Bearer "+bearer)
	req.Header.Set("Content-Type", "application/json")

	res, err := c.http.Do(req)
	if err != nil {
		return fmt.Errorf("do request: %w", err)
	}
	defer res.Body.Close()
	if res.StatusCode == http.StatusOK {
		return nil
	}

	var apiErr struct {
		Error struct {
			Status  string `json:"status"`
			Message string `json:"message"`
			Details []struct {
				ErrorCode string `json:"errorCode"` //nolint:tagliatelle // FCM's field name
			} `json:"details"`
		} `json:"error"`
	}
	raw, _ := io.ReadAll(io.LimitReader(res.Body, maxResponseBytes))
	_ = json.Unmarshal(raw, &apiErr)
	for _, d := range apiErr.Error.Details {
		if d.ErrorCode == "UNREGISTERED" {
			return fmt.Errorf("%w: %s", ErrUnregistered, apiErr.Error.Message)
		}
	}
	if res.StatusCode == http.StatusNotFound {
		return fmt.Errorf("%w: %s", ErrUnregistered, apiErr.Error.Message)
	}
	return fmt.Errorf("status %d: %s", res.StatusCode, apiErr.Error.Message)
}

type accessToken struct {
	mu        sync.Mutex
	bearer    string
	expiresAt time.Time
}

type assertionClaims struct {
	jwt.RegisteredClaims

	Scope string `json:"scope"`
}

func (a *accessToken) get(ctx context.Context, c Client, now time.Time) (string, error) {
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.bearer != "" && now.Before(a.expiresAt) {
		return a.bearer, nil
	}

	account := c.cfg.ServiceAccount
	assertion, err := jwt.NewWithClaims(jwt.SigningMethodRS256, assertionClaims{
		Issuer:    account.ClientEmail,
		Audience:  jwt.ClaimStrings{c.cfg.TokenURL},
		IssuedAt:  jwt.NewNumericDate(now),
		ExpiresAt: jwt.NewNumericDate(now.Add(assertionTTL)),
		Scope:     scope,
	}).SignedString(account.PrivateKey)
	if err != nil {
		return "", fmt.Errorf("sign assertion: %w", err)
	}

	form := url.Values{
		"grant_type": {"urn:ietf:params:oauth:grant-type:jwt-bearer"},
		"assertion":  {assertion},
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.cfg.TokenURL, strings.NewReader(form.Encode()))
	if err != nil {
		return "", fmt.Errorf("new token request: %w", err)
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")

	res, err := c.http.Do(req)
	if err != nil {
		return "", fmt.Errorf("fetch access token: %w", err)
	}
	defer res.Body.Close()
	raw, err := io.ReadAll(io.LimitReader(res.Body, maxResponseBytes))
	if err != nil {
		return "", fmt.Errorf("read token response: %w", err)
	}
	if res.StatusCode != http.StatusOK {
		return "", fmt.Errorf("fetch access token: status %d: %s", res.StatusCode, raw)
	}
	var body struct {
		AccessToken string `json:"access_token"`
		ExpiresIn   int    `json:"expires_in"`
	}
	if err := json.Unmarshal(raw, &body); err != nil {
		return "", fmt.Errorf("decode token response: %w", err)
	}
	if body.AccessToken == "" {
		return "", errors.New("fetch access token: empty access_token")
	}
	a.bearer = body.AccessToken
	a.expiresAt = now.Add(time.Duration(body.ExpiresIn)*time.Second - tokenMargin)
	return a.bearer, nil
}
