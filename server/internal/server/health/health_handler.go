package health

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"time"

	"github.com/mickamy/LocateDo/internal/infra/storage/db"
	"github.com/mickamy/LocateDo/internal/lib/logger"
)

const healthPingTimeout = 2 * time.Second

type pinger interface {
	Ping(ctx context.Context) error
}

type Health struct {
	pingers map[string]pinger
}

var _ http.Handler = Health{}

func NewHealth(writer db.Writer, reader db.Reader) Health {
	return Health{pingers: map[string]pinger{
		"writer": writer,
		"reader": reader,
	}}
}

func (h Health) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(r.Context(), healthPingTimeout)
	defer cancel()

	var errs []error
	for name, p := range h.pingers {
		if err := p.Ping(ctx); err != nil {
			errs = append(errs, fmt.Errorf("ping %s: %w", name, err))
		}
	}
	if err := errors.Join(errs...); err != nil {
		logger.Error(ctx, "health check failed", "error", err)
		http.Error(w, "unavailable", http.StatusServiceUnavailable)
		return
	}

	_, _ = w.Write([]byte("ok"))
}
