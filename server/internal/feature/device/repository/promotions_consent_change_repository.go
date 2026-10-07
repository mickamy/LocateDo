package repository

import (
	"context"
	"fmt"

	"github.com/mickamy/LocateDo/internal/feature/device/model"
	"github.com/mickamy/LocateDo/internal/feature/device/queries"
	"github.com/mickamy/LocateDo/internal/infra/storage/db"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
)

type PromotionsConsentChange interface {
	Insert(ctx context.Context, c model.PromotionsConsentChange) error
	Bind(tx tx.Tx) PromotionsConsentChange
}

type promotionsConsentChange struct {
	q *queries.Queries
}

var _ PromotionsConsentChange = promotionsConsentChange{}

func NewPromotionsConsentChange(reader db.Reader) PromotionsConsentChange {
	return promotionsConsentChange{q: queries.New(reader)}
}

func (r promotionsConsentChange) Bind(tx tx.Tx) PromotionsConsentChange {
	return promotionsConsentChange{q: queries.New(tx.DBTX())}
}

func (r promotionsConsentChange) Insert(ctx context.Context, c model.PromotionsConsentChange) error {
	if err := r.q.InsertPromotionsConsentChange(ctx, queries.InsertPromotionsConsentChangeParams{
		DeviceID:  c.DeviceID,
		UserID:    c.UserID,
		Consented: c.Consented,
		ChangedAt: c.ChangedAt,
	}); err != nil {
		return fmt.Errorf("insert promotions consent change: %w", err)
	}
	return nil
}
