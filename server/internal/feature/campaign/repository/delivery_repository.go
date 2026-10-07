package repository

import (
	"context"
	"fmt"
	"time"
	"uuid"

	"github.com/mickamy/LocateDo/internal/feature/campaign/model"
	"github.com/mickamy/LocateDo/internal/feature/campaign/queries"
	dmodel "github.com/mickamy/LocateDo/internal/feature/device/model"
	"github.com/mickamy/LocateDo/internal/infra/storage/db"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
)

type Delivery interface {
	// ListRecipients pages, in id order after the given one, through the
	// devices consenting in the campaign's language that it has not tried.
	ListRecipients(ctx context.Context, c model.Campaign, after uuid.UUID, limit int) ([]dmodel.Device, error)
	// ClaimDelivery records that the campaign is about to be sent to the
	// device, and reports false when it was already, so nothing is sent twice.
	ClaimDelivery(ctx context.Context, campaignID, deviceID uuid.UUID, at time.Time) (bool, error)
	MarkDelivered(ctx context.Context, campaignID, deviceID uuid.UUID, at time.Time) error
	// ReleaseDelivery drops a claim whose push surely failed, so a retry may
	// send it again.
	ReleaseDelivery(ctx context.Context, campaignID, deviceID uuid.UUID) error
	Bind(tx tx.Tx) Delivery
}

type delivery struct {
	q *queries.Queries
}

var _ Delivery = delivery{}

func NewDelivery(reader db.Reader) Delivery {
	return delivery{q: queries.New(reader)}
}

func (r delivery) Bind(tx tx.Tx) Delivery {
	return delivery{q: queries.New(tx.DBTX())}
}

func (r delivery) ListRecipients(
	ctx context.Context,
	c model.Campaign,
	after uuid.UUID,
	limit int,
) ([]dmodel.Device, error) {
	rows, err := r.q.ListRecipients(ctx, queries.ListRecipientsParams{
		Language:   string(c.Language),
		After:      after,
		CampaignID: c.ID,
		PageSize:   int32(limit), //nolint:gosec // a page size
	})
	if err != nil {
		return nil, fmt.Errorf("list recipients: %w", err)
	}
	devices := make([]dmodel.Device, 0, len(rows))
	for _, row := range rows {
		var env dmodel.APNsEnvironment
		if row.ApnsEnvironment != nil {
			env = dmodel.APNsEnvironment(*row.ApnsEnvironment)
		}
		devices = append(devices, dmodel.Device{
			ID:              row.ID,
			Platform:        dmodel.Platform(row.Platform),
			PushToken:       row.PushToken,
			APNsEnvironment: env,
		})
	}
	return devices, nil
}

func (r delivery) ClaimDelivery(ctx context.Context, campaignID, deviceID uuid.UUID, at time.Time) (bool, error) {
	n, err := r.q.ClaimDelivery(ctx, queries.ClaimDeliveryParams{
		CampaignID:  campaignID,
		DeviceID:    deviceID,
		AttemptedAt: at,
	})
	if err != nil {
		return false, fmt.Errorf("claim delivery: %w", err)
	}
	return n == 1, nil
}

func (r delivery) MarkDelivered(ctx context.Context, campaignID, deviceID uuid.UUID, at time.Time) error {
	if err := r.q.MarkDelivered(ctx, queries.MarkDeliveredParams{
		CampaignID: campaignID,
		DeviceID:   deviceID,
		SentAt:     &at,
	}); err != nil {
		return fmt.Errorf("mark delivered: %w", err)
	}
	return nil
}

func (r delivery) ReleaseDelivery(ctx context.Context, campaignID, deviceID uuid.UUID) error {
	if err := r.q.ReleaseDelivery(ctx, queries.ReleaseDeliveryParams{
		CampaignID: campaignID,
		DeviceID:   deviceID,
	}); err != nil {
		return fmt.Errorf("release delivery: %w", err)
	}
	return nil
}
