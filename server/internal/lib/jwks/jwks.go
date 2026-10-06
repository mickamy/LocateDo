// Package jwks fetches the RSA public keys an identity provider publishes
// and caches them by key id.
package jwks

import (
	"context"
	"crypto/rsa"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math/big"
	"net/http"
	"sync"
	"time"
)

const (
	// An unknown kid triggers a refetch, but at most once per refetchMinWait
	// so bogus kids cannot hammer the provider.
	refetchMinWait   = time.Minute
	maxResponseBytes = 1 << 20
)

type Cache struct {
	url  string
	http *http.Client

	mu        sync.RWMutex
	keys      map[string]*rsa.PublicKey
	fetchedAt time.Time
}

func New(url string, httpClient *http.Client) *Cache {
	return &Cache{url: url, http: httpClient}
}

func (c *Cache) Key(ctx context.Context, kid string, now time.Time) (*rsa.PublicKey, error) {
	if key, ok := c.get(kid); ok {
		return key, nil
	}
	if !c.shouldRefetch(now) {
		return nil, fmt.Errorf("unknown key id %q", kid)
	}
	if err := c.fetch(ctx, now); err != nil {
		return nil, err
	}
	if key, ok := c.get(kid); ok {
		return key, nil
	}
	return nil, fmt.Errorf("unknown key id %q", kid)
}

func (c *Cache) get(kid string) (*rsa.PublicKey, bool) {
	c.mu.RLock()
	defer c.mu.RUnlock()
	key, ok := c.keys[kid]
	return key, ok
}

func (c *Cache) shouldRefetch(now time.Time) bool {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.fetchedAt.IsZero() || now.Sub(c.fetchedAt) >= refetchMinWait
}

func (c *Cache) fetch(ctx context.Context, now time.Time) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, c.url, nil)
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

	c.mu.Lock()
	defer c.mu.Unlock()
	c.keys = keys
	c.fetchedAt = now
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
