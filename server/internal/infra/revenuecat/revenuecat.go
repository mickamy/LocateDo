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

var ErrNotConfigured = errors.New("revenuecat api key is not configured")

type Entitlements interface {
	// Active reports whether the app user holds the entitlement right now.
	Active(ctx context.Context, appUserID string, now time.Time) (bool, error)
}

var _ Entitlements = Client{}

type Config struct {
	BaseURL     string
	APIKey      string
	Entitlement string
}

// Client reads a subscriber's entitlements from the RevenueCat REST API.
type Client struct {
	cfg  Config
	http *http.Client
}

func NewClient(cfg Config, httpClient *http.Client) Client {
	return Client{cfg: cfg, http: httpClient}
}

func (c Client) Active(ctx context.Context, appUserID string, now time.Time) (bool, error) {
	if c.cfg.APIKey == "" {
		return false, ErrNotConfigured
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet,
		c.cfg.BaseURL+"/v1/subscribers/"+url.PathEscape(appUserID), nil)
	if err != nil {
		return false, fmt.Errorf("new request: %w", err)
	}
	req.Header.Set("Authorization", "Bearer "+c.cfg.APIKey)

	res, err := c.http.Do(req)
	if err != nil {
		return false, fmt.Errorf("do request: %w", err)
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK {
		return false, fmt.Errorf("get subscriber: status %d", res.StatusCode)
	}

	var body struct {
		Subscriber struct {
			Entitlements map[string]struct {
				ExpiresDate *time.Time `json:"expires_date"`
			} `json:"entitlements"`
		} `json:"subscriber"`
	}
	if err := json.NewDecoder(io.LimitReader(res.Body, maxResponseBytes)).Decode(&body); err != nil {
		return false, fmt.Errorf("decode subscriber: %w", err)
	}
	e, ok := body.Subscriber.Entitlements[c.cfg.Entitlement]
	if !ok {
		return false, nil
	}
	return e.ExpiresDate == nil || e.ExpiresDate.After(now), nil
}
