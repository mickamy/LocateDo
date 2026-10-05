package usecase

import (
	"context"
	"fmt"
	"uuid"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/household/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/internal/outbox"
)

type SetPlanInput struct {
	OwnerID uuid.UUID
	Plan    model.Plan
}

type SetPlanOutput struct {
	HouseholdID uuid.UUID
	Changed     bool
}

// SetPlan sets the plan of the household the user owns. A changed plan wakes
// the members, since plans do not travel as changes.
type SetPlan struct {
	_          di.Infra             `di:"embed"`
	transactor tx.Transactor        `di:""`
	households repository.Household `di:""`
	messages   outbox.Repository    `di:""`
}

func (uc SetPlan) Do(ctx context.Context, in SetPlanInput) (SetPlanOutput, error) {
	var out SetPlanOutput
	if err := uc.transactor.WithTx(ctx, func(tx tx.Tx) error {
		households := uc.households.Bind(tx)
		h, err := households.FindByOwner(ctx, in.OwnerID)
		if err != nil {
			return fmt.Errorf("find household: %w", err)
		}
		changed, err := households.SetPlan(ctx, h.ID, in.Plan)
		if err != nil {
			return fmt.Errorf("set plan: %w", err)
		}
		out = SetPlanOutput{HouseholdID: h.ID, Changed: changed}
		if !changed {
			return nil
		}
		if err := uc.messages.Bind(tx).Enqueue(ctx, outbox.PushHousehold(h.ID, clock.Now(ctx))); err != nil {
			return fmt.Errorf("enqueue push: %w", err)
		}
		return nil
	}); err != nil {
		return SetPlanOutput{}, fmt.Errorf("set plan: %w", err)
	}
	return out, nil
}
