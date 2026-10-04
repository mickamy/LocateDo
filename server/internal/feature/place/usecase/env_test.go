package usecase_test

import (
	"testing"
	"uuid"

	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/di"
	hmodel "github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/place/model"
	"github.com/mickamy/LocateDo/internal/feature/place/repository"
	"github.com/mickamy/LocateDo/internal/feature/place/usecase"
	"github.com/mickamy/LocateDo/test/tinfra"
)

type env struct {
	infra       di.Infra
	places      repository.Place
	putPlace    *usecase.PutPlace
	deletePlace *usecase.DeletePlace
}

func newEnv(t *testing.T) *env {
	t.Helper()

	infra := tinfra.New(t)
	return &env{
		infra:       infra,
		places:      repository.NewPlace(infra.Reader),
		putPlace:    usecase.NewPutPlace(infra),
		deletePlace: usecase.NewDeletePlace(infra),
	}
}

func (e *env) user(t *testing.T) uuid.UUID {
	t.Helper()

	var id uuid.UUID
	require.NoError(t, e.infra.Writer.QueryRow(t.Context(),
		"INSERT INTO users DEFAULT VALUES RETURNING id").Scan(&id))
	return id
}

// household creates a household with an owner and returns both IDs.
func (e *env) household(t *testing.T, plan hmodel.Plan) (uuid.UUID, uuid.UUID) {
	t.Helper()

	ownerID := e.user(t)
	var householdID uuid.UUID
	require.NoError(t, e.infra.Writer.QueryRow(t.Context(),
		"INSERT INTO households (owner_id, plan) VALUES ($1, $2) RETURNING id", ownerID, plan).Scan(&householdID))
	_, err := e.infra.Writer.Exec(t.Context(),
		"INSERT INTO memberships (household_id, user_id, role) VALUES ($1, $2, 'owner')", householdID, ownerID)
	require.NoError(t, err)
	return ownerID, householdID
}

func (e *env) member(t *testing.T, householdID uuid.UUID) uuid.UUID {
	t.Helper()

	userID := e.user(t)
	_, err := e.infra.Writer.Exec(t.Context(),
		"INSERT INTO memberships (household_id, user_id, role) VALUES ($1, $2, 'member')", householdID, userID)
	require.NoError(t, err)
	return userID
}

func (e *env) place(t *testing.T, householdID uuid.UUID) uuid.UUID {
	t.Helper()

	var id uuid.UUID
	require.NoError(t, e.infra.Writer.QueryRow(t.Context(),
		`INSERT INTO places (household_id, name, lat, lng)
		 VALUES ($1, 'store', 35.0, 139.0) RETURNING id`, householdID).Scan(&id))
	return id
}

func (e *env) todo(t *testing.T, householdID, placeID uuid.UUID) uuid.UUID {
	t.Helper()

	var id uuid.UUID
	require.NoError(t, e.infra.Writer.QueryRow(t.Context(),
		"INSERT INTO todos (household_id, place_id, title) VALUES ($1, $2, 'milk') RETURNING id",
		householdID, placeID).Scan(&id))
	return id
}

func (e *env) count(t *testing.T, table string, householdID uuid.UUID) int {
	t.Helper()

	var n int
	require.NoError(t, e.infra.Writer.QueryRow(t.Context(),
		"SELECT count(*) FROM "+table+" WHERE household_id = $1", householdID).Scan(&n))
	return n
}

// inHousehold fills the ids the fixture leaves zero.
func inHousehold(householdID uuid.UUID) func(m *model.Place) {
	return func(m *model.Place) {
		m.ID = uuid.NewV7()
		m.HouseholdID = householdID
	}
}
