package job

import (
	"context"
	"fmt"
	"time"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/internal/lib/logger"
	"github.com/mickamy/LocateDo/internal/outbox"
)

// DeadWindow is how far back ReportOutbox counts messages that died.
const DeadWindow = time.Hour

// ReportOutbox logs the queue's health; CloudWatch turns the fields into
// metrics (deploy/tofu/prod/monitoring.tf), so their names are a contract.
type ReportOutbox struct {
	_        di.Infra          `di:"embed"`
	messages outbox.Repository `di:""`
}

func (j ReportOutbox) Run(ctx context.Context) error {
	now := clock.Now(ctx)
	h, err := j.messages.Health(ctx, now, now.Add(-DeadWindow))
	if err != nil {
		return fmt.Errorf("report outbox: %w", err)
	}
	logger.Info(ctx, "outbox health",
		"overdue_seconds", int64(h.Overdue.Seconds()),
		"oldest_seconds", int64(h.Oldest.Seconds()),
		"dead_last_hour", h.Dead)
	return nil
}
