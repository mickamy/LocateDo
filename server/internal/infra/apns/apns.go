package apns

import (
	"bytes"
	"context"
	"crypto/ecdsa"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"sync"
	"time"
	"uuid"

	"github.com/golang-jwt/jwt/v5"

	"github.com/mickamy/LocateDo/internal/lib/logger"
)

const (
	ProductionURL = "https://api.push.apple.com"
	SandboxURL    = "https://api.sandbox.push.apple.com"

	// Apple rejects provider tokens older than an hour and throttles renewals
	// more frequent than every 20 minutes.
	tokenTTL         = 50 * time.Minute
	maxResponseBytes = 1 << 16
)

// ErrNotDelivered means the push surely did not reach APNs: it failed before
// being sent, or APNs answered with an error. Other errors leave it unknown.
var ErrNotDelivered = errors.New("apns: push not delivered")

// ErrUnregistered means the device token is no longer valid for this app;
// the caller should forget it.
var ErrUnregistered = fmt.Errorf("%w: device token is no longer valid", ErrNotDelivered)

// Environment is the APNs environment a device token was issued for: Xcode
// builds get sandbox tokens, TestFlight and App Store builds production ones.
type Environment string

const (
	Sandbox    Environment = "sandbox"
	Production Environment = "production"
)

type Pusher interface {
	// Wake sends a silent push that lets the app run in the background.
	Wake(ctx context.Context, env Environment, deviceToken string, now time.Time) error
	// Promote shows a quiet notification for a promotional campaign.
	Promote(ctx context.Context, env Environment, deviceToken string, p Promotion, now time.Time) error
	// NotifyCompletion tells a to-do's creator, with sound, that a household
	// member checked it off.
	NotifyCompletion(ctx context.Context, env Environment, deviceToken string, n CompletionNotice, now time.Time) error
}

type Promotion struct {
	CampaignID uuid.UUID
	Title      string
	Body       string
	// URL is opened on tap when set.
	URL string
}

type CompletionNotice struct {
	Body string
	// Count is how many to-dos the notice covers.
	Count int
}

var _ Pusher = Client{}

type Config struct {
	ProductionURL string
	SandboxURL    string
	Topic         string
	TeamID        string
	KeyID         string
	PrivateKey    *ecdsa.PrivateKey
}

// Client sends pushes over APNs with token-based authentication.
type Client struct {
	cfg   Config
	http  *http.Client
	token *providerToken
}

func NewClient(cfg Config, httpClient *http.Client) Client {
	return Client{cfg: cfg, http: httpClient, token: &providerToken{}}
}

var background = []byte(`{"aps":{"content-available":1}}`)

func (c Client) Wake(ctx context.Context, env Environment, deviceToken string, now time.Time) error {
	return c.send(ctx, env, deviceToken, push{pushType: "background", priority: "5", body: background}, now)
}

func (c Client) Promote(ctx context.Context, env Environment, deviceToken string, p Promotion, now time.Time) error {
	payload := map[string]any{
		"aps": map[string]any{
			"alert":              map[string]string{"title": p.Title, "body": p.Body},
			"interruption-level": "passive",
			"thread-id":          "campaign",
		},
		"campaign_id": p.CampaignID.String(),
	}
	if p.URL != "" {
		payload["url"] = p.URL
	}
	body, err := json.Marshal(payload)
	if err != nil {
		return fmt.Errorf("%w: encode promotion: %w", ErrNotDelivered, err)
	}
	return c.send(ctx, env, deviceToken, push{
		pushType: "alert", priority: "5", collapseID: p.CampaignID.String(), body: body,
	}, now)
}

func (c Client) NotifyCompletion(
	ctx context.Context,
	env Environment,
	deviceToken string,
	n CompletionNotice,
	now time.Time,
) error {
	body, err := json.Marshal(map[string]any{
		"aps": map[string]any{
			"alert":     map[string]string{"body": n.Body},
			"sound":     "default",
			"thread-id": "completion",
		},
		"type":  "completion",
		"count": n.Count,
	})
	if err != nil {
		return fmt.Errorf("%w: encode completion notice: %w", ErrNotDelivered, err)
	}
	return c.send(ctx, env, deviceToken, push{pushType: "alert", priority: "10", body: body}, now)
}

type push struct {
	pushType   string
	priority   string
	collapseID string
	body       []byte
}

func (c Client) send(ctx context.Context, env Environment, deviceToken string, p push, now time.Time) error {
	if c.cfg.PrivateKey == nil {
		logger.Debug(ctx, "apns is not configured; dropping push", "token", deviceToken, "push_type", p.pushType)
		return nil
	}
	var baseURL string
	switch env {
	case Sandbox:
		baseURL = c.cfg.SandboxURL
	case Production:
		baseURL = c.cfg.ProductionURL
	default:
		return fmt.Errorf("%w: unknown APNs environment %q", ErrNotDelivered, env)
	}
	bearer, err := c.token.get(c.cfg, now)
	if err != nil {
		return fmt.Errorf("%w: %w", ErrNotDelivered, err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost,
		baseURL+"/3/device/"+deviceToken, bytes.NewReader(p.body))
	if err != nil {
		return fmt.Errorf("%w: new request: %w", ErrNotDelivered, err)
	}
	req.Header.Set("Authorization", "bearer "+bearer)
	req.Header.Set("Apns-Topic", c.cfg.Topic)
	req.Header.Set("Apns-Push-Type", p.pushType)
	req.Header.Set("Apns-Priority", p.priority)
	if p.collapseID != "" {
		req.Header.Set("Apns-Collapse-Id", p.collapseID)
	}
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
		Reason string `json:"reason"`
	}
	raw, _ := io.ReadAll(io.LimitReader(res.Body, maxResponseBytes))
	_ = json.Unmarshal(raw, &apiErr)
	if res.StatusCode == http.StatusGone || apiErr.Reason == "BadDeviceToken" {
		return fmt.Errorf("%w: %s", ErrUnregistered, apiErr.Reason)
	}
	return fmt.Errorf("%w: status %d: %s", ErrNotDelivered, res.StatusCode, apiErr.Reason)
}

type providerToken struct {
	mu       sync.Mutex
	signed   string
	issuedAt time.Time
}

func (p *providerToken) get(cfg Config, now time.Time) (string, error) {
	p.mu.Lock()
	defer p.mu.Unlock()
	if p.signed != "" && now.Sub(p.issuedAt) < tokenTTL {
		return p.signed, nil
	}

	t := jwt.NewWithClaims(jwt.SigningMethodES256, jwt.RegisteredClaims{
		Issuer:   cfg.TeamID,
		IssuedAt: jwt.NewNumericDate(now),
	})
	t.Header["kid"] = cfg.KeyID
	signed, err := t.SignedString(cfg.PrivateKey)
	if err != nil {
		return "", fmt.Errorf("sign provider token: %w", err)
	}
	p.signed = signed
	p.issuedAt = now
	return signed, nil
}
