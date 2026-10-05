package usecase_test

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/household/usecase"
	susecase "github.com/mickamy/LocateDo/internal/feature/sync/usecase"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestForceResync_resetsDevicesPastTheRestoredVersion(t *testing.T) {
	t.Parallel()

	// arrange: a device synced up to a version the restored database no longer has
	d := tdb.New(t)
	h := d.Seeder.Household(t, model.PlanFree)
	d.Seeder.Place(t, h.ID)
	deviceCursor := d.Seeder.Version(t, h.ID) + 5
	clearPushes(t, d)

	// act
	n, err := usecase.NewForceResync(d.Infra()).Do(fixedClock(t))

	// assert
	require.NoError(t, err)
	assert.Equal(t, 1, n)
	assert.Equal(t, 1, pushes(t, d), "members are woken to pull")
	out, err := susecase.NewPull(d.Infra()).Do(t.Context(), susecase.PullInput{
		HouseholdID: h.ID, RequestedHouseholdID: h.ID, Cursor: deviceCursor,
	})
	require.NoError(t, err)
	assert.True(t, out.Reset)
	assert.Len(t, out.Changes, 2, "the membership and the place, in full")
}

func TestForceResync_writesAfterwardStillReset(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	h := d.Seeder.Household(t, model.PlanFree)
	deviceCursor := d.Seeder.Version(t, h.ID) + 5
	_, err := usecase.NewForceResync(d.Infra()).Do(fixedClock(t))
	require.NoError(t, err)

	// act: new writes push the version on past the old cursor
	for range 10 {
		d.Seeder.Place(t, h.ID)
	}
	out, err := susecase.NewPull(d.Infra()).Do(t.Context(), susecase.PullInput{
		HouseholdID: h.ID, RequestedHouseholdID: h.ID, Cursor: deviceCursor,
	})

	// assert
	require.NoError(t, err)
	assert.True(t, out.Reset)
}

func TestForceResync_freshDevicesSyncNormally(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	h := d.Seeder.Household(t, model.PlanFree)
	_, err := usecase.NewForceResync(d.Infra()).Do(fixedClock(t))
	require.NoError(t, err)

	// act
	out, err := susecase.NewPull(d.Infra()).Do(t.Context(), susecase.PullInput{
		HouseholdID: h.ID, RequestedHouseholdID: h.ID,
	})

	// assert
	require.NoError(t, err)
	assert.False(t, out.Reset, "a first sync is not a reset")
	resumed, err := susecase.NewPull(d.Infra()).Do(t.Context(), susecase.PullInput{
		HouseholdID: h.ID, RequestedHouseholdID: h.ID, Cursor: out.Cursor,
	})
	require.NoError(t, err)
	assert.False(t, resumed.Reset, "a cursor issued after the resync is current")
	assert.Empty(t, resumed.Changes)
}
