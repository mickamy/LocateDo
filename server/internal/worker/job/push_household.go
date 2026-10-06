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
	"github.com/mickamy/LocateDo/internal/infra/fcm"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/internal/lib/logger"
	"github.com/mickamy/LocateDo/internal/outbox"
)

// PushHousehold delivers outbox.KindPushHousehold, which the sync triggers
// enqueue: it wakes every member's device, over APNs or FCM, so it pulls.
// Tokens the push service no longer accepts are forgotten; any other failure
// retries the whole message, and the extra pushes it repeats are harmless.
type PushHousehold struct {
	_          di.Infra          `di:"embed"`
	transactor tx.Transactor     `di:""`
	devices    repository.Device `di:""`
	apns       apns.Pusher       `di:""`
	fcm        fcm.Pusher        `di:""`
}

var _ outbox.Handler = PushHousehold{}

type pushTally struct {
	woken     int
	forgotten int
	failed    []error
}

func (j PushHousehold) Handle(ctx context.Context, m outbox.Message) error {
	var payload struct {
		HouseholdID uuid.UUID `json:"household_id"`
	}
	if err := json.Unmarshal(m.Payload, &payload); err != nil {
		return fmt.Errorf("decode push: %w", err)
	}
	now := clock.Now(ctx)

	var tally pushTally
	err := j.wake(ctx, payload.HouseholdID, model.PlatformIOS, &tally, func(d model.Device) error {
		return j.apns.Wake(ctx, apns.Environment(d.APNsEnvironment), d.PushToken, now)
	})
	if err != nil {
		return err
	}
	err = j.wake(ctx, payload.HouseholdID, model.PlatformAndroid, &tally, func(d model.Device) error {
		return j.fcm.Wake(ctx, d.PushToken, now)
	})
	if err != nil {
		return err
	}

	logger.Info(ctx, "woke household devices", "household_id", payload.HouseholdID,
		"woken", tally.woken, "forgotten", tally.forgotten, "failed", len(tally.failed))
	if err := errors.Join(tally.failed...); err != nil {
		return fmt.Errorf("wake devices: %w", err)
	}
	return nil
}

func (j PushHousehold) wake(
	ctx context.Context,
	householdID uuid.UUID,
	platform model.Platform,
	tally *pushTally,
	send func(model.Device) error,
) error {
	devices, err := j.devices.ListByHousehold(ctx, householdID, platform)
	if err != nil {
		return fmt.Errorf("list %s devices: %w", platform, err)
	}
	for _, d := range devices {
		err := send(d)
		switch {
		case err == nil:
			tally.woken++
		case errors.Is(err, apns.ErrUnregistered), errors.Is(err, fcm.ErrUnregistered):
			logger.Info(ctx, "forgetting a device token the push service rejected",
				"user_id", d.UserID, "platform", platform, "error", err)
			if err := j.forget(ctx, platform, d.PushToken); err != nil {
				tally.failed = append(tally.failed, err)
				continue
			}
			tally.forgotten++
		default:
			tally.failed = append(tally.failed, err)
		}
	}
	return nil
}

func (j PushHousehold) forget(ctx context.Context, platform model.Platform, token string) error {
	if err := j.transactor.WithTx(ctx, func(tx tx.Tx) error {
		return j.devices.Bind(tx).DeleteByToken(ctx, platform, token)
	}); err != nil {
		return fmt.Errorf("forget device: %w", err)
	}
	return nil
}
