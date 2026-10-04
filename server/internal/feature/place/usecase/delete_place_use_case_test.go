package usecase_test

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	hmodel "github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/place/usecase"
)

func TestDeletePlace(t *testing.T) {
	t.Parallel()

	// arrange
	e := newEnv(t)
	h := e.seed.Household(t, hmodel.PlanFree)
	memberID := e.seed.Member(t, h.ID)
	placeID := e.seed.Place(t, h.ID)
	e.seed.Todo(t, h.ID, placeID)

	// act
	err := e.deletePlace.Do(t.Context(), usecase.DeletePlaceInput{UserID: memberID, PlaceID: placeID})
	again := e.deletePlace.Do(t.Context(), usecase.DeletePlaceInput{UserID: memberID, PlaceID: placeID})

	// assert
	require.NoError(t, err)
	require.NoError(t, again, "a retry after the place is gone succeeds")
	assert.Zero(t, e.seed.Count(t, "places", h.ID))
	assert.Zero(t, e.seed.Count(t, "todos", h.ID), "todos go with the place")
}

func TestDeletePlace_anotherHousehold(t *testing.T) {
	t.Parallel()

	// arrange
	e := newEnv(t)
	h := e.seed.Household(t, hmodel.PlanFree)
	other := e.seed.Household(t, hmodel.PlanFree)
	placeID := e.seed.Place(t, other.ID)

	// act
	err := e.deletePlace.Do(t.Context(), usecase.DeletePlaceInput{UserID: h.OwnerID, PlaceID: placeID})

	// assert
	require.NoError(t, err)
	assert.Equal(t, 1, e.seed.Count(t, "places", other.ID))
}

func TestDeletePlace_outsider(t *testing.T) {
	t.Parallel()

	// arrange
	e := newEnv(t)
	h := e.seed.Household(t, hmodel.PlanFree)
	placeID := e.seed.Place(t, h.ID)

	// act
	err := e.deletePlace.Do(t.Context(), usecase.DeletePlaceInput{UserID: e.seed.User(t), PlaceID: placeID})

	// assert
	require.ErrorIs(t, err, aerrors.ErrPermissionDenied)
	assert.Equal(t, 1, e.seed.Count(t, "places", h.ID))
}
