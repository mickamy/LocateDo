package usecase_test

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/household/repository"
	"github.com/mickamy/LocateDo/internal/feature/household/usecase"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestCreateHousehold_imports(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	userID := d.Seeder.User(t)
	householdID := newID()

	// act
	out, err := usecase.NewCreateHousehold(d.Infra()).Do(fixedClock(t), usecase.CreateHouseholdInput{
		UserID:      userID,
		HouseholdID: householdID,
		Contents:    contents(),
	})

	// assert
	require.NoError(t, err)
	assert.Equal(t, householdID, out.Household.ID)
	assert.Equal(t, userID, out.Household.OwnerID)
	assert.Equal(t, model.PlanFree, out.Household.Plan)
	assert.Equal(t, d.Seeder.Version(t, householdID), out.Household.Version, "the version after the import")
	m, err := repository.NewMembership(d.Reader).FindByUser(t.Context(), userID)
	require.NoError(t, err)
	assert.Equal(t, model.RoleOwner, m.Role)
	assert.Equal(t, 1, d.Seeder.Count(t, "categories", householdID))
	assert.Equal(t, 1, d.Seeder.Count(t, "places", householdID))
	assert.Equal(t, 2, d.Seeder.Count(t, "todos", householdID))
	var checks int
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"SELECT count(*) FROM outbox_messages WHERE kind = 'sync_entitlement' AND payload->>'user_id' = $1",
		userID.String()).Scan(&checks))
	assert.Equal(t, 1, checks, "a purchase made before signing in is picked up")
}

func TestCreateHousehold_retryIsIdempotent(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	createHousehold := usecase.NewCreateHousehold(d.Infra())
	in := usecase.CreateHouseholdInput{UserID: d.Seeder.User(t), HouseholdID: newID(), Contents: contents()}
	first, err := createHousehold.Do(fixedClock(t), in)
	require.NoError(t, err)

	// act
	second, err := createHousehold.Do(fixedClock(t), in)

	// assert
	require.NoError(t, err)
	assert.Equal(t, first.Household.ID, second.Household.ID)
	assert.Equal(t, first.Household.OwnerID, second.Household.OwnerID)
	assert.Equal(t, 1, d.Seeder.Count(t, "places", in.HouseholdID))
}

func TestCreateHousehold_alreadyInAnother(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	createHousehold := usecase.NewCreateHousehold(d.Infra())
	userID := d.Seeder.User(t)
	_, err := createHousehold.Do(fixedClock(t), usecase.CreateHouseholdInput{UserID: userID, HouseholdID: newID()})
	require.NoError(t, err)

	// act
	_, err = createHousehold.Do(fixedClock(t), usecase.CreateHouseholdInput{UserID: userID, HouseholdID: newID()})

	// assert
	require.ErrorIs(t, err, aerrors.ErrConflict)
}

func TestCreateHousehold_unknownReference(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	c := contents()
	unknown := newID()
	c.Places[0].CategoryID = &unknown
	householdID := newID()

	// act
	_, err := usecase.NewCreateHousehold(d.Infra()).Do(fixedClock(t), usecase.CreateHouseholdInput{
		UserID: d.Seeder.User(t), HouseholdID: householdID, Contents: c,
	})

	// assert
	require.ErrorIs(t, err, aerrors.ErrInvalidArgument)
	_, err = repository.NewHousehold(d.Reader).Find(t.Context(), householdID)
	require.ErrorIs(t, err, aerrors.ErrNotFound, "nothing is left behind")
}
