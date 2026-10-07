package repository

import (
	"context"
	"errors"
	"fmt"
	"time"
	"uuid"

	"github.com/jackc/pgx/v5"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/campaign/model"
	"github.com/mickamy/LocateDo/internal/feature/campaign/queries"
	dmodel "github.com/mickamy/LocateDo/internal/feature/device/model"
	"github.com/mickamy/LocateDo/internal/infra/storage/db"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
)

type Campaign interface {
	// CountAudience counts the devices consenting to promotions in the language.
	CountAudience(ctx context.Context, language dmodel.Language) (model.Audience, error)
	// FindLastSentAt reports when the last campaign in the language finished
	// sending, or NotFound.
	FindLastSentAt(ctx context.Context, language dmodel.Language) (time.Time, error)
	CountUnsent(ctx context.Context, language dmodel.Language) (int, error)
	Insert(ctx context.Context, c model.Campaign) (uuid.UUID, error)
	Bind(tx tx.Tx) Campaign
}

type campaign struct {
	q *queries.Queries
}

var _ Campaign = campaign{}

func NewCampaign(reader db.Reader) Campaign {
	return campaign{q: queries.New(reader)}
}

func (r campaign) Bind(tx tx.Tx) Campaign {
	return campaign{q: queries.New(tx.DBTX())}
}

func (r campaign) CountAudience(ctx context.Context, language dmodel.Language) (model.Audience, error) {
	rows, err := r.q.CountAudience(ctx, string(language))
	if err != nil {
		return model.Audience{}, fmt.Errorf("count audience: %w", err)
	}
	var a model.Audience
	for _, row := range rows {
		switch dmodel.Platform(row.Platform) {
		case dmodel.PlatformIOS:
			a.IOS = int(row.Devices)
		case dmodel.PlatformAndroid:
			a.Android = int(row.Devices)
		}
	}
	return a, nil
}

func (r campaign) FindLastSentAt(ctx context.Context, language dmodel.Language) (time.Time, error) {
	sentAt, err := r.q.FindLastSentAt(ctx, string(language))
	if errors.Is(err, pgx.ErrNoRows) {
		return time.Time{}, aerrors.NotFound("sent campaign")
	}
	if err != nil {
		return time.Time{}, fmt.Errorf("find last sent campaign: %w", err)
	}
	return *sentAt, nil
}

func (r campaign) CountUnsent(ctx context.Context, language dmodel.Language) (int, error) {
	n, err := r.q.CountUnsent(ctx, string(language))
	if err != nil {
		return 0, fmt.Errorf("count unsent campaigns: %w", err)
	}
	return int(n), nil
}

func (r campaign) Insert(ctx context.Context, c model.Campaign) (uuid.UUID, error) {
	var u *string
	if c.URL != "" {
		u = &c.URL
	}
	id, err := r.q.InsertCampaign(ctx, queries.InsertCampaignParams{
		Language:    string(c.Language),
		Title:       c.Title,
		Body:        c.Body,
		Url:         u,
		TargetCount: int32(c.TargetCount), //nolint:gosec // device counts stay far below 2^31
		CreatedAt:   c.CreatedAt,
	})
	if err != nil {
		return uuid.UUID{}, fmt.Errorf("insert campaign: %w", err)
	}
	return id, nil
}
