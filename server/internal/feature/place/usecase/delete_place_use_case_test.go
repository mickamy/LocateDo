package usecase_test

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	hmodel "github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/place/usecase"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestDeletePlace(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	deletePlace := usecase.NewDeletePlace(d.Infra())
	h := d.Seeder.Household(t, hmodel.PlanFree)
	memberID := d.Seeder.Member(t, h.ID)
	placeID := d.Seeder.Place(t, h.ID)
	d.Seeder.Todo(t, h.ID, placeID)

	// act
	err := deletePlace.Do(t.Context(), usecase.DeletePlaceInput{UserID: memberID, PlaceID: placeID})
	again := deletePlace.Do(t.Context(), usecase.DeletePlaceInput{UserID: memberID, PlaceID: placeID})

	// assert
	require.NoError(t, err)
	require.NoError(t, again, "a retry after the place is gone succeeds")
	assert.Zero(t, d.Seeder.Count(t, "places", h.ID))
	assert.Zero(t, d.Seeder.Count(t, "todos", h.ID), "todos go with the place")
}

func TestDeletePlace_anotherHousehold(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	other := d.Seeder.Household(t, hmodel.PlanFree)
	placeID := d.Seeder.Place(t, other.ID)

	// act
	err := usecase.NewDeletePlace(d.Infra()).Do(t.Context(), usecase.DeletePlaceInput{UserID: h.OwnerID, PlaceID: placeID})

	// assert
	require.NoError(t, err)
	assert.Equal(t, 1, d.Seeder.Count(t, "places", other.ID))
}

func TestDeletePlace_outsider(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	placeID := d.Seeder.Place(t, h.ID)

	// act
	err := usecase.NewDeletePlace(d.Infra()).Do(t.Context(), usecase.DeletePlaceInput{
		UserID: d.Seeder.User(t), PlaceID: placeID,
	})

	// assert
	require.ErrorIs(t, err, aerrors.ErrPermissionDenied)
	assert.Equal(t, 1, d.Seeder.Count(t, "places", h.ID))
}
