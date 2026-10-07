package job

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"time"
	"uuid"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	cmodel "github.com/mickamy/LocateDo/internal/feature/campaign/model"
	crepository "github.com/mickamy/LocateDo/internal/feature/campaign/repository"
	dmodel "github.com/mickamy/LocateDo/internal/feature/device/model"
	drepository "github.com/mickamy/LocateDo/internal/feature/device/repository"
	"github.com/mickamy/LocateDo/internal/infra/apns"
	"github.com/mickamy/LocateDo/internal/infra/fcm"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/internal/lib/logger"
	"github.com/mickamy/LocateDo/internal/outbox"
)

const (
	recipientPageSize = 500
	// campaignAttempts is the attempt after which devices that still fail are
	// given up on, so the campaign is marked sent and the next one may follow.
	campaignAttempts = 5
)

// SendCampaign delivers outbox.KindSendCampaign: it sends the campaign to the
// consenting devices in its language, at most once each. A device is claimed
// before its push goes out and only released when the push surely failed, so
// a retry tries just those; a push whose outcome is unknown is never repeated.
// Registrations the push service no longer accepts are forgotten.
type SendCampaign struct {
	_          di.Infra             `di:"embed"`
	transactor tx.Transactor        `di:""`
	campaigns  crepository.Campaign `di:""`
	deliveries crepository.Delivery `di:""`
	devices    drepository.Device   `di:""`
	apns       apns.Pusher          `di:""`
	fcm        fcm.Pusher           `di:""`
}

var _ outbox.Handler = SendCampaign{}

type campaignTally struct {
	sent         int
	unregistered int
	// failed pushes surely did not go out and are retried.
	failed []error
	// uncertain pushes may have gone out and are never retried.
	uncertain int
}

func (j SendCampaign) Handle(ctx context.Context, m outbox.Message) error {
	var payload struct {
		CampaignID uuid.UUID `json:"campaign_id"`
	}
	if err := json.Unmarshal(m.Payload, &payload); err != nil {
		return fmt.Errorf("decode campaign: %w", err)
	}
	c, err := j.campaigns.Find(ctx, payload.CampaignID)
	if errors.Is(err, aerrors.ErrNotFound) {
		logger.Warn(ctx, "campaign not found", "campaign_id", payload.CampaignID)
		return nil
	}
	if err != nil {
		return fmt.Errorf("find campaign: %w", err)
	}
	if c.SentAt != nil {
		return nil
	}

	var tally campaignTally
	var after uuid.UUID
	for {
		devices, err := j.deliveries.ListRecipients(ctx, c, after, recipientPageSize)
		if err != nil {
			return fmt.Errorf("list recipients: %w", err)
		}
		for _, d := range devices {
			if err := ctx.Err(); err != nil {
				return fmt.Errorf("send campaign %s: %w", c.ID, err)
			}
			j.send(ctx, c, d, &tally)
		}
		if len(devices) < recipientPageSize {
			break
		}
		after = devices[len(devices)-1].ID
	}

	if len(tally.failed) > 0 && m.Attempts+1 < campaignAttempts {
		logger.Warn(ctx, "campaign partly sent", "campaign_id", c.ID, "language", c.Language,
			"sent", tally.sent, "unregistered", tally.unregistered, "failed", len(tally.failed),
			"uncertain", tally.uncertain)
		return fmt.Errorf("send campaign %s: %w", c.ID, errors.Join(tally.failed...))
	}
	if err := j.transactor.WithTx(ctx, func(tx tx.Tx) error {
		return j.campaigns.Bind(tx).Finish(ctx, c.ID, clock.Now(ctx), len(tally.failed))
	}); err != nil {
		return fmt.Errorf("finish campaign: %w", err)
	}
	logger.Info(ctx, "campaign sent", "campaign_id", c.ID, "language", c.Language,
		"sent", tally.sent, "unregistered", tally.unregistered, "failed", len(tally.failed),
		"uncertain", tally.uncertain)
	return nil
}

func (j SendCampaign) send(ctx context.Context, c cmodel.Campaign, d dmodel.Device, tally *campaignTally) {
	now := clock.Now(ctx)
	var claimed bool
	if err := j.transactor.WithTx(ctx, func(tx tx.Tx) error {
		var err error
		claimed, err = j.deliveries.Bind(tx).ClaimDelivery(ctx, c.ID, d.ID, now)
		if err != nil {
			return fmt.Errorf("claim: %w", err)
		}
		return nil
	}); err != nil {
		tally.failed = append(tally.failed, fmt.Errorf("claim delivery: %w", err))
		return
	}
	if !claimed {
		return
	}

	err := j.promote(ctx, c, d, now)
	switch {
	case err == nil:
		tally.sent++
		if err := j.transactor.WithTx(ctx, func(tx tx.Tx) error {
			return j.deliveries.Bind(tx).MarkDelivered(ctx, c.ID, d.ID, clock.Now(ctx))
		}); err != nil {
			logger.Error(ctx, "campaign sent but not marked delivered", "campaign_id", c.ID, "device_id", d.ID,
				"error", err)
		}
	case errors.Is(err, apns.ErrUnregistered), errors.Is(err, fcm.ErrUnregistered):
		logger.Info(ctx, "forgetting a device registration the push service rejected",
			"device_id", d.ID, "platform", d.Platform, "error", err)
		if err := j.forget(ctx, c, d); err != nil {
			logger.Error(ctx, "failed to forget a device", "device_id", d.ID, "error", err)
			tally.uncertain++
			return
		}
		tally.unregistered++
	case errors.Is(err, apns.ErrNotDelivered), errors.Is(err, fcm.ErrNotDelivered):
		if err := j.transactor.WithTx(ctx, func(tx tx.Tx) error {
			return j.deliveries.Bind(tx).ReleaseDelivery(ctx, c.ID, d.ID)
		}); err != nil {
			logger.Error(ctx, "failed to release a delivery for retry", "campaign_id", c.ID, "device_id", d.ID,
				"error", err)
			tally.uncertain++
			return
		}
		tally.failed = append(tally.failed, err)
	default:
		logger.Warn(ctx, "campaign push may or may not have gone out; not retrying it",
			"campaign_id", c.ID, "device_id", d.ID, "error", err)
		tally.uncertain++
	}
}

func (j SendCampaign) promote(ctx context.Context, c cmodel.Campaign, d dmodel.Device, now time.Time) error {
	var err error
	switch d.Platform {
	case dmodel.PlatformIOS:
		err = j.apns.Promote(ctx, apns.Environment(d.APNsEnvironment), d.PushToken, apns.Promotion{
			CampaignID: c.ID, Title: c.Title, Body: c.Body, URL: c.URL,
		}, now)
	case dmodel.PlatformAndroid:
		err = j.fcm.Promote(ctx, d.PushToken, fcm.Promotion{
			CampaignID: c.ID, Title: c.Title, Body: c.Body, URL: c.URL,
		}, now)
	default:
		err = fmt.Errorf("unknown platform %q", d.Platform)
	}
	if err != nil {
		return fmt.Errorf("promote to %s: %w", d.Platform, err)
	}
	return nil
}

func (j SendCampaign) forget(ctx context.Context, c cmodel.Campaign, d dmodel.Device) error {
	if err := j.transactor.WithTx(ctx, func(tx tx.Tx) error {
		if err := j.devices.Bind(tx).Delete(ctx, d.ID); err != nil {
			return fmt.Errorf("delete device: %w", err)
		}
		if err := j.campaigns.Bind(tx).AddUnregistered(ctx, c.ID); err != nil {
			return fmt.Errorf("count unregistered: %w", err)
		}
		return nil
	}); err != nil {
		return fmt.Errorf("forget device: %w", err)
	}
	return nil
}
