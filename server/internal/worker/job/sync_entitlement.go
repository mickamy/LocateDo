package job

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"uuid"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/household/usecase"
	"github.com/mickamy/LocateDo/internal/infra/revenuecat"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/internal/lib/logger"
	"github.com/mickamy/LocateDo/internal/outbox"
)

// SyncEntitlement delivers outbox.KindSyncEntitlement: it sets the plan of
// the household the user owns from what RevenueCat says now, whatever event
// prompted the check, so late or repeated webhooks cannot leave a wrong plan.
type SyncEntitlement struct {
	_            di.Infra                `di:"embed"`
	setPlan      *usecase.SetPlan        `di:""`
	entitlements revenuecat.Entitlements `di:""`
}

var _ outbox.Handler = SyncEntitlement{}

func (j SyncEntitlement) Handle(ctx context.Context, m outbox.Message) error {
	var payload struct {
		UserID uuid.UUID `json:"user_id"`
	}
	if err := json.Unmarshal(m.Payload, &payload); err != nil {
		return fmt.Errorf("decode sync entitlement: %w", err)
	}

	active, err := j.entitlements.Active(ctx, payload.UserID.String(), clock.Now(ctx))
	if errors.Is(err, revenuecat.ErrNotConfigured) {
		logger.Debug(ctx, "revenuecat is not configured; leaving the plan alone", "user_id", payload.UserID)
		return nil
	}
	if err != nil {
		return fmt.Errorf("read entitlement: %w", err)
	}
	plan := model.PlanFree
	if active {
		plan = model.PlanPro
	}

	_, err = j.setPlan.Do(ctx, usecase.SetPlanInput{OwnerID: payload.UserID, Plan: plan})
	if errors.Is(err, aerrors.ErrNotFound) {
		return nil
	}
	if err != nil {
		return fmt.Errorf("sync entitlement: %w", err)
	}
	return nil
}
