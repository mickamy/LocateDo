package health_test

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/assert"

	"github.com/mickamy/LocateDo/internal/server/health"
	"github.com/mickamy/LocateDo/test/tinfra"
)

func TestHealth_ServeHTTP(t *testing.T) {
	t.Parallel()

	// arrange
	infra := tinfra.New(t)
	h := health.NewHealth(infra.Writer, infra.Reader)
	rec := httptest.NewRecorder()
	req := httptest.NewRequestWithContext(t.Context(), http.MethodGet, "/healthz", nil)

	// act
	h.ServeHTTP(rec, req)

	// assert
	assert.Equal(t, http.StatusOK, rec.Code)
	assert.Equal(t, "ok", rec.Body.String())
}

func TestHealth_ServeHTTP_pingFails(t *testing.T) {
	t.Parallel()

	// arrange
	h := health.NewHealthWithPingers(map[string]health.Pinger{
		"ok":     fakePinger{},
		"broken": fakePinger{err: errors.New("connection refused")},
	})
	rec := httptest.NewRecorder()
	req := httptest.NewRequestWithContext(t.Context(), http.MethodGet, "/healthz", nil)

	// act
	h.ServeHTTP(rec, req)

	// assert
	assert.Equal(t, http.StatusServiceUnavailable, rec.Code)
	assert.NotContains(t, rec.Body.String(), "connection refused")
}

type fakePinger struct {
	err error
}

func (p fakePinger) Ping(context.Context) error {
	return p.err
}
