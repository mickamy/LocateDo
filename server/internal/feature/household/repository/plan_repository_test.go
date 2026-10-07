package repository_test

import (
	"testing"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/household/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestPlan_Set(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	plans := repository.NewPlan(d.Reader)
	h := d.Seeder.Household(t, model.PlanFree)

	// act
	var changed, unchanged bool
	d.InTx(t, func(tx tx.Tx) {
		var err error
		changed, err = plans.Bind(tx).Set(t.Context(), h.ID, model.PlanPro)
		require.NoError(t, err)
		unchanged, err = plans.Bind(tx).Set(t.Context(), h.ID, model.PlanPro)
		require.NoError(t, err)
	})

	// assert
	assert.True(t, changed)
	assert.False(t, unchanged)
}

func TestPlan_Owners(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	plans := repository.NewPlan(d.Reader)
	pro := d.Seeder.Household(t, model.PlanPro)
	d.Seeder.Household(t, model.PlanFree)

	// act
	got, err := plans.Owners(t.Context(), model.PlanPro)

	// assert
	require.NoError(t, err)
	assert.Equal(t, []uuid.UUID{pro.OwnerID}, got)
}
