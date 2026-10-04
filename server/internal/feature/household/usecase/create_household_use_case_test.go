package usecase_test

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/household/usecase"
)

func TestCreateHousehold_imports(t *testing.T) {
	t.Parallel()

	// arrange
	e := newEnv(t)
	userID := e.seed.User(t)
	householdID := newID()

	// act
	out, err := e.createHousehold.Do(e.ctx, usecase.CreateHouseholdInput{
		UserID:      userID,
		HouseholdID: householdID,
		Contents:    contents(),
	})

	// assert
	require.NoError(t, err)
	assert.Equal(t, householdID, out.Household.ID)
	assert.Equal(t, userID, out.Household.OwnerID)
	assert.Equal(t, model.PlanFree, out.Household.Plan)
	m, err := e.memberships.FindByUser(t.Context(), userID)
	require.NoError(t, err)
	assert.Equal(t, model.RoleOwner, m.Role)
	assert.Equal(t, 1, e.seed.Count(t, "categories", householdID))
	assert.Equal(t, 1, e.seed.Count(t, "places", householdID))
	assert.Equal(t, 2, e.seed.Count(t, "todos", householdID))
}

func TestCreateHousehold_retryIsIdempotent(t *testing.T) {
	t.Parallel()

	// arrange
	e := newEnv(t)
	in := usecase.CreateHouseholdInput{UserID: e.seed.User(t), HouseholdID: newID(), Contents: contents()}
	first, err := e.createHousehold.Do(e.ctx, in)
	require.NoError(t, err)

	// act
	second, err := e.createHousehold.Do(e.ctx, in)

	// assert
	require.NoError(t, err)
	assert.Equal(t, first.Household, second.Household)
	assert.Equal(t, 1, e.seed.Count(t, "places", in.HouseholdID))
}

func TestCreateHousehold_alreadyInAnother(t *testing.T) {
	t.Parallel()

	// arrange
	e := newEnv(t)
	userID := e.seed.User(t)
	_, err := e.createHousehold.Do(e.ctx, usecase.CreateHouseholdInput{UserID: userID, HouseholdID: newID()})
	require.NoError(t, err)

	// act
	_, err = e.createHousehold.Do(e.ctx, usecase.CreateHouseholdInput{UserID: userID, HouseholdID: newID()})

	// assert
	require.ErrorIs(t, err, aerrors.ErrConflict)
}

func TestCreateHousehold_unknownReference(t *testing.T) {
	t.Parallel()

	// arrange
	e := newEnv(t)
	c := contents()
	unknown := newID()
	c.Places[0].CategoryID = &unknown
	householdID := newID()

	// act
	_, err := e.createHousehold.Do(e.ctx, usecase.CreateHouseholdInput{
		UserID: e.seed.User(t), HouseholdID: householdID, Contents: c,
	})

	// assert
	require.ErrorIs(t, err, aerrors.ErrInvalidArgument)
	_, err = e.households.Find(t.Context(), householdID)
	require.ErrorIs(t, err, aerrors.ErrNotFound, "nothing is left behind")
}
