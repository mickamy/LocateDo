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
	_, householdID := e.household(t, hmodel.PlanFree)
	memberID := e.member(t, householdID)
	placeID := e.place(t, householdID)
	e.todo(t, householdID, placeID)

	// act
	err := e.deletePlace.Do(t.Context(), usecase.DeletePlaceInput{UserID: memberID, PlaceID: placeID})
	again := e.deletePlace.Do(t.Context(), usecase.DeletePlaceInput{UserID: memberID, PlaceID: placeID})

	// assert
	require.NoError(t, err)
	require.NoError(t, again, "a retry after the place is gone succeeds")
	assert.Zero(t, e.count(t, "places", householdID))
	assert.Zero(t, e.count(t, "todos", householdID), "todos go with the place")
}

func TestDeletePlace_anotherHousehold(t *testing.T) {
	t.Parallel()

	// arrange
	e := newEnv(t)
	ownerID, _ := e.household(t, hmodel.PlanFree)
	_, other := e.household(t, hmodel.PlanFree)
	placeID := e.place(t, other)

	// act
	err := e.deletePlace.Do(t.Context(), usecase.DeletePlaceInput{UserID: ownerID, PlaceID: placeID})

	// assert
	require.NoError(t, err)
	assert.Equal(t, 1, e.count(t, "places", other))
}

func TestDeletePlace_outsider(t *testing.T) {
	t.Parallel()

	// arrange
	e := newEnv(t)
	_, householdID := e.household(t, hmodel.PlanFree)
	placeID := e.place(t, householdID)

	// act
	err := e.deletePlace.Do(t.Context(), usecase.DeletePlaceInput{UserID: e.user(t), PlaceID: placeID})

	// assert
	require.ErrorIs(t, err, aerrors.ErrPermissionDenied)
	assert.Equal(t, 1, e.count(t, "places", householdID))
}
