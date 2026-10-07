package job

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"uuid"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	dmodel "github.com/mickamy/LocateDo/internal/feature/device/model"
	drepository "github.com/mickamy/LocateDo/internal/feature/device/repository"
	hrepository "github.com/mickamy/LocateDo/internal/feature/household/repository"
	tmodel "github.com/mickamy/LocateDo/internal/feature/todo/model"
	trepository "github.com/mickamy/LocateDo/internal/feature/todo/repository"
	"github.com/mickamy/LocateDo/internal/infra/apns"
	"github.com/mickamy/LocateDo/internal/infra/fcm"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/internal/lib/logger"
	"github.com/mickamy/LocateDo/internal/outbox"
)

// NotifyCompletion delivers outbox.KindNotifyCompletion: it tells a to-do's
// creator that another member checked off their to-dos, in one notice per
// device. The to-dos are marked announced before anything is sent, so a
// notice goes out at most once and a failed push is not retried.
type NotifyCompletion struct {
	_           di.Infra               `di:"embed"`
	transactor  tx.Transactor          `di:""`
	todos       trepository.Todo       `di:""`
	memberships hrepository.Membership `di:""`
	devices     drepository.Device     `di:""`
	apns        apns.Pusher            `di:""`
	fcm         fcm.Pusher             `di:""`
}

var _ outbox.Handler = NotifyCompletion{}

func (j NotifyCompletion) Handle(ctx context.Context, m outbox.Message) error {
	var payload struct {
		HouseholdID uuid.UUID `json:"household_id"`
		CreatorID   uuid.UUID `json:"creator_id"`
		CompleterID uuid.UUID `json:"completer_id"`
	}
	if err := json.Unmarshal(m.Payload, &payload); err != nil {
		return fmt.Errorf("decode completion notice: %w", err)
	}

	creator, err := j.memberships.FindByUser(ctx, payload.CreatorID)
	if errors.Is(err, aerrors.ErrNotFound) || (err == nil && creator.HouseholdID != payload.HouseholdID) {
		return nil
	}
	if err != nil {
		return fmt.Errorf("find creator's membership: %w", err)
	}
	var completerName string
	completer, err := j.memberships.FindByUser(ctx, payload.CompleterID)
	switch {
	case err == nil:
		completerName = completer.DisplayName
	case !errors.Is(err, aerrors.ErrNotFound):
		return fmt.Errorf("find completer's membership: %w", err)
	}
	devices, err := j.devices.ListCompletionNoticeDevices(ctx, payload.CreatorID)
	if err != nil {
		return fmt.Errorf("list devices: %w", err)
	}

	now := clock.Now(ctx)
	var todos []tmodel.CompletedTodo
	if err := j.transactor.WithTx(ctx, func(tx tx.Tx) error {
		var err error
		todos, err = j.todos.Bind(tx).ClaimCompletionNotice(
			ctx, payload.HouseholdID, payload.CreatorID, payload.CompleterID, now)
		if err != nil {
			return fmt.Errorf("claim: %w", err)
		}
		return nil
	}); err != nil {
		return fmt.Errorf("claim completion notice: %w", err)
	}
	if len(todos) == 0 {
		return nil
	}

	var sent, failed int
	for _, d := range devices {
		body := tmodel.CompletionNotice(d.Language, completerName, todos)
		err := j.notify(ctx, d, body, len(todos))
		switch {
		case err == nil:
			sent++
		case errors.Is(err, apns.ErrUnregistered), errors.Is(err, fcm.ErrUnregistered):
			logger.Info(ctx, "forgetting a device registration the push service rejected",
				"device_id", d.ID, "platform", d.Platform, "error", err)
			if err := j.forget(ctx, d); err != nil {
				logger.Error(ctx, "failed to forget a device", "device_id", d.ID, "error", err)
			}
		default:
			logger.Warn(ctx, "completion notice not sent; not retrying it", "device_id", d.ID, "error", err)
			failed++
		}
	}
	logger.Info(ctx, "completion notice sent", "household_id", payload.HouseholdID,
		"todos", len(todos), "devices", sent, "failed", failed)
	return nil
}

func (j NotifyCompletion) notify(ctx context.Context, d dmodel.Device, body string, count int) error {
	now := clock.Now(ctx)
	var err error
	switch d.Platform {
	case dmodel.PlatformIOS:
		err = j.apns.NotifyCompletion(ctx, apns.Environment(d.APNsEnvironment), d.PushToken,
			apns.CompletionNotice{Body: body, Count: count}, now)
	case dmodel.PlatformAndroid:
		err = j.fcm.NotifyCompletion(ctx, d.PushToken, fcm.CompletionNotice{Body: body, Count: count}, now)
	default:
		err = fmt.Errorf("unknown platform %q", d.Platform)
	}
	if err != nil {
		return fmt.Errorf("notify %s: %w", d.Platform, err)
	}
	return nil
}

func (j NotifyCompletion) forget(ctx context.Context, d dmodel.Device) error {
	if err := j.transactor.WithTx(ctx, func(tx tx.Tx) error {
		return j.devices.Bind(tx).Delete(ctx, d.ID)
	}); err != nil {
		return fmt.Errorf("forget device: %w", err)
	}
	return nil
}
