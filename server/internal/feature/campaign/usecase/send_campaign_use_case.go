package usecase

import (
	"context"
	"errors"
	"fmt"
	"time"
	"uuid"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/campaign/model"
	"github.com/mickamy/LocateDo/internal/feature/campaign/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/internal/outbox"
)

type SendCampaignInput struct {
	Campaign model.Campaign
	// DryRun checks the campaign and counts its audience without writing.
	DryRun bool
	// Force skips the interval between campaigns in one language.
	Force bool
}

type SendCampaignOutput struct {
	// CampaignID is zero on a dry run.
	CampaignID uuid.UUID
	Audience   model.Audience
}

// SendCampaign records a campaign and queues its delivery to the devices
// consenting to promotions in its language.
type SendCampaign struct {
	_          di.Infra            `di:"embed"`
	transactor tx.Transactor       `di:""`
	campaigns  repository.Campaign `di:""`
	messages   outbox.Repository   `di:""`
}

func (uc SendCampaign) Do(ctx context.Context, in SendCampaignInput) (SendCampaignOutput, error) {
	c := in.Campaign
	if err := c.Validate(); err != nil {
		return SendCampaignOutput{}, fmt.Errorf("send campaign: %w", err)
	}
	now := clock.Now(ctx)
	c.CreatedAt = now

	var out SendCampaignOutput
	if err := uc.transactor.WithTx(ctx, func(tx tx.Tx) error {
		campaigns := uc.campaigns.Bind(tx)

		unsent, err := campaigns.CountUnsent(ctx, c.Language)
		if err != nil {
			return fmt.Errorf("count unsent campaigns: %w", err)
		}
		if unsent > 0 {
			return aerrors.Precondition(fmt.Sprintf("a %s campaign is still being sent", c.Language))
		}
		if !in.Force {
			lastSentAt, err := campaigns.FindLastSentAt(ctx, c.Language)
			if err != nil && !errors.Is(err, aerrors.ErrNotFound) {
				return fmt.Errorf("find last sent campaign: %w", err)
			}
			if err == nil && now.Sub(lastSentAt) < model.Interval {
				return aerrors.Precondition(fmt.Sprintf("a %s campaign was sent at %s, less than %d days ago",
					c.Language, lastSentAt.Format(time.RFC3339), int(model.Interval.Hours()/24)))
			}
		}

		out.Audience, err = campaigns.CountAudience(ctx, c.Language)
		if err != nil {
			return fmt.Errorf("count audience: %w", err)
		}
		if in.DryRun {
			return nil
		}

		c.TargetCount = out.Audience.Total()
		out.CampaignID, err = campaigns.Insert(ctx, c)
		if err != nil {
			return fmt.Errorf("insert campaign: %w", err)
		}
		if err := uc.messages.Bind(tx).Enqueue(ctx, outbox.SendCampaign(out.CampaignID, now)); err != nil {
			return fmt.Errorf("enqueue send: %w", err)
		}
		return nil
	}); err != nil {
		return SendCampaignOutput{}, fmt.Errorf("send campaign: %w", err)
	}
	return out, nil
}
