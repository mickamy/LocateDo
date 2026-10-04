package usecase_test

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	hmodel "github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/place/fixture"
	"github.com/mickamy/LocateDo/internal/feature/place/model"
	"github.com/mickamy/LocateDo/internal/feature/place/repository"
	"github.com/mickamy/LocateDo/internal/feature/place/usecase"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestPutPlace(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	h := d.Seeder.Household(t, hmodel.PlanPro)
	p := fixture.Place(func(m *model.Place) { m.HouseholdID = h.ID })

	// act
	err := usecase.NewPutPlace(d.Infra()).Do(t.Context(), usecase.PutPlaceInput{HouseholdID: h.ID, Place: p})

	// assert
	require.NoError(t, err)
	exists, err := repository.NewPlace(d.Reader).Exists(t.Context(), p.ID, h.ID)
	require.NoError(t, err)
	assert.True(t, exists)
}

func TestPutPlace_anotherHousehold(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	h := d.Seeder.Household(t, hmodel.PlanPro)
	other := d.Seeder.Household(t, hmodel.PlanPro)
	p := fixture.Place(func(m *model.Place) { m.HouseholdID = other.ID })

	// act
	err := usecase.NewPutPlace(d.Infra()).Do(t.Context(), usecase.PutPlaceInput{HouseholdID: h.ID, Place: p})

	// assert
	require.ErrorIs(t, err, aerrors.ErrPermissionDenied)
	assert.Zero(t, d.Seeder.Count(t, "places", other.ID))
}

func TestPutPlace_freeLimit(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name     string
		plan     hmodel.Plan
		existing int
		want     error
	}{
		{
			name:     "free household below the limit",
			plan:     hmodel.PlanFree,
			existing: hmodel.MaxFreePlaces - 1,
		},
		{
			name:     "free household at the limit",
			plan:     hmodel.PlanFree,
			existing: hmodel.MaxFreePlaces,
			want:     aerrors.ErrPrecondition,
		},
		{
			name:     "pro household at the limit",
			plan:     hmodel.PlanPro,
			existing: hmodel.MaxFreePlaces,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			// arrange
			d := tdb.New(t)
			h := d.Seeder.Household(t, tt.plan)
			for range tt.existing {
				d.Seeder.Place(t, h.ID)
			}
			p := fixture.Place(func(m *model.Place) { m.HouseholdID = h.ID })

			// act
			err := usecase.NewPutPlace(d.Infra()).Do(t.Context(), usecase.PutPlaceInput{HouseholdID: h.ID, Place: p})

			// assert
			if tt.want != nil {
				require.ErrorIs(t, err, tt.want)
				assert.Equal(t, tt.existing, d.Seeder.Count(t, "places", h.ID))
				return
			}
			require.NoError(t, err)
			assert.Equal(t, tt.existing+1, d.Seeder.Count(t, "places", h.ID))
		})
	}
}

func TestPutPlace_freeHouseholdOverTheLimitKeepsEditing(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	putPlace := usecase.NewPutPlace(d.Infra())
	h := d.Seeder.Household(t, hmodel.PlanFree)
	p := fixture.Place(func(m *model.Place) { m.HouseholdID = h.ID })
	require.NoError(t, putPlace.Do(t.Context(), usecase.PutPlaceInput{HouseholdID: h.ID, Place: p}))
	for range hmodel.MaxFreePlaces {
		d.Seeder.Place(t, h.ID)
	}
	p.Name = "Grocery"

	// act
	err := putPlace.Do(t.Context(), usecase.PutPlaceInput{HouseholdID: h.ID, Place: p})

	// assert
	require.NoError(t, err)
	var name string
	require.NoError(t, d.Writer.QueryRow(t.Context(), "SELECT name FROM places WHERE id = $1", p.ID).Scan(&name))
	assert.Equal(t, "Grocery", name)
	assert.Equal(t, hmodel.MaxFreePlaces+1, d.Seeder.Count(t, "places", h.ID))
}
