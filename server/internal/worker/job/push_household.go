package job

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"uuid"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/feature/device/model"
	"github.com/mickamy/LocateDo/internal/feature/device/repository"
	"github.com/mickamy/LocateDo/internal/infra/apns"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/internal/lib/logger"
	"github.com/mickamy/LocateDo/internal/outbox"
)

// PushHousehold delivers outbox.KindPushHousehold, which the sync triggers
// enqueue: it wakes every member's iOS device so it pulls. Tokens APNs no
// longer accepts are forgotten; any other failure retries the whole message,
// and the extra pushes it repeats are harmless.
type PushHousehold struct {
	_          di.Infra          `di:"embed"`
	transactor tx.Transactor     `di:""`
	devices    repository.Device `di:""`
	pusher     apns.Pusher       `di:""`
}

var _ outbox.Handler = PushHousehold{}

func (j PushHousehold) Handle(ctx context.Context, m outbox.Message) error {
	var payload struct {
		HouseholdID uuid.UUID `json:"household_id"`
	}
	if err := json.Unmarshal(m.Payload, &payload); err != nil {
		return fmt.Errorf("decode push: %w", err)
	}
	devices, err := j.devices.ListByHousehold(ctx, payload.HouseholdID, model.PlatformIOS)
	if err != nil {
		return fmt.Errorf("list devices: %w", err)
	}

	var failed []error
	for _, d := range devices {
		err := j.pusher.Wake(ctx, apns.Environment(d.APNsEnvironment), d.PushToken, clock.Now(ctx))
		switch {
		case errors.Is(err, apns.ErrUnregistered):
			logger.Info(ctx, "forgetting a device token apns rejected",
				"user_id", d.UserID, "apns_environment", d.APNsEnvironment, "error", err)
			if err := j.forget(ctx, d.PushToken); err != nil {
				failed = append(failed, err)
			}
		case err != nil:
			failed = append(failed, err)
		}
	}
	if err := errors.Join(failed...); err != nil {
		return fmt.Errorf("wake devices: %w", err)
	}
	return nil
}

func (j PushHousehold) forget(ctx context.Context, token string) error {
	if err := j.transactor.WithTx(ctx, func(tx tx.Tx) error {
		return j.devices.Bind(tx).DeleteByToken(ctx, model.PlatformIOS, token)
	}); err != nil {
		return fmt.Errorf("forget device: %w", err)
	}
	return nil
}
