package usecase_test

import (
	"testing"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/household/repository"
	"github.com/mickamy/LocateDo/internal/feature/household/usecase"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestSetPlan(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name    string
		plan    model.Plan
		to      model.Plan
		changed bool
	}{
		{name: "upgrade wakes the members", plan: model.PlanFree, to: model.PlanPro, changed: true},
		{name: "downgrade wakes the members", plan: model.PlanPro, to: model.PlanFree, changed: true},
		{name: "same plan stays quiet", plan: model.PlanPro, to: model.PlanPro},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			// arrange
			d := tdb.New(t)
			h := d.Seeder.Household(t, tt.plan)
			clearPushes(t, d)

			// act
			out, err := usecase.NewSetPlan(d.Infra()).Do(fixedClock(t), usecase.SetPlanInput{OwnerID: h.OwnerID, Plan: tt.to})

			// assert
			require.NoError(t, err)
			assert.Equal(t, usecase.SetPlanOutput{HouseholdID: h.ID, Changed: tt.changed}, out)
			got, err := repository.NewHousehold(d.Reader).FindByOwner(t.Context(), h.OwnerID)
			require.NoError(t, err)
			assert.Equal(t, tt.to, got.Plan)
			assert.Equal(t, tt.changed, pushes(t, d) == 1)
		})
	}
}

func TestSetPlan_notAnOwner(t *testing.T) {
	t.Parallel()

	tests := map[string]func(t *testing.T, d tdb.DB) uuid.UUID{
		"a member": func(t *testing.T, d tdb.DB) uuid.UUID {
			return d.Seeder.Member(t, d.Seeder.Household(t, model.PlanFree).ID)
		},
		"a user without a household": func(t *testing.T, d tdb.DB) uuid.UUID {
			return d.Seeder.User(t)
		},
		"an unknown user": func(*testing.T, tdb.DB) uuid.UUID {
			return uuid.NewV7()
		},
	}
	for name, arrange := range tests {
		t.Run(name, func(t *testing.T) {
			t.Parallel()

			// arrange
			d := tdb.New(t)
			userID := arrange(t, d)

			// act
			_, err := usecase.NewSetPlan(d.Infra()).Do(fixedClock(t), usecase.SetPlanInput{OwnerID: userID, Plan: model.PlanPro})

			// assert
			require.ErrorIs(t, err, aerrors.ErrNotFound)
		})
	}
}
