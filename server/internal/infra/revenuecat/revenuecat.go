package revenuecat

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"time"
)

const maxResponseBytes = 1 << 20

var ErrNotConfigured = errors.New("revenuecat api access is not configured")

type Entitlements interface {
	// Active reports whether the app user holds the entitlement right now.
	Active(ctx context.Context, appUserID string, now time.Time) (bool, error)
}

var _ Entitlements = Client{}

type Config struct {
	BaseURL       string
	APIKey        string
	ProjectID     string
	EntitlementID string
}

// Client reads a customer's active entitlements from the RevenueCat REST API v2.
type Client struct {
	cfg  Config
	http *http.Client
}

func NewClient(cfg Config, httpClient *http.Client) Client {
	return Client{cfg: cfg, http: httpClient}
}

func (c Client) Active(ctx context.Context, appUserID string, now time.Time) (bool, error) {
	if c.cfg.APIKey == "" || c.cfg.ProjectID == "" || c.cfg.EntitlementID == "" {
		return false, ErrNotConfigured
	}
	path := fmt.Sprintf("/v2/projects/%s/customers/%s/active_entitlements",
		url.PathEscape(c.cfg.ProjectID), url.PathEscape(appUserID))
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, c.cfg.BaseURL+path, nil)
	if err != nil {
		return false, fmt.Errorf("new request: %w", err)
	}
	req.Header.Set("Authorization", "Bearer "+c.cfg.APIKey)

	res, err := c.http.Do(req)
	if err != nil {
		return false, fmt.Errorf("do request: %w", err)
	}
	defer res.Body.Close()
	if res.StatusCode == http.StatusNotFound {
		return false, nil
	}
	if res.StatusCode != http.StatusOK {
		return false, fmt.Errorf("list active entitlements: status %d", res.StatusCode)
	}

	var body struct {
		Items []struct {
			EntitlementID string `json:"entitlement_id"`
			ExpiresAt     *int64 `json:"expires_at"`
		} `json:"items"`
	}
	if err := json.NewDecoder(io.LimitReader(res.Body, maxResponseBytes)).Decode(&body); err != nil {
		return false, fmt.Errorf("decode active entitlements: %w", err)
	}
	for _, e := range body.Items {
		if e.EntitlementID != c.cfg.EntitlementID {
			continue
		}
		if e.ExpiresAt == nil || time.UnixMilli(*e.ExpiresAt).After(now) {
			return true, nil
		}
	}
	return false, nil
}
