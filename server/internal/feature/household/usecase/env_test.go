package usecase_test

import (
	"context"
	"testing"
	"time"
	"uuid"

	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/household/repository"
	"github.com/mickamy/LocateDo/internal/feature/household/usecase"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/test/tinfra"
)

var now = time.Date(2026, 10, 3, 12, 0, 0, 0, time.UTC)

type env struct {
	ctx             context.Context //nolint:containedctx // test fixture
	infra           di.Infra
	households      repository.Household
	memberships     repository.Membership
	createHousehold *usecase.CreateHousehold
	createInvite    *usecase.CreateInvite
	acceptInvite    *usecase.AcceptInvite
	removeMember    *usecase.RemoveMember
}

func newEnv(t *testing.T) *env {
	t.Helper()

	infra := tinfra.New(t)
	return &env{
		ctx:             clock.Set(t.Context(), clock.NewFixed(now)),
		infra:           infra,
		households:      repository.NewHousehold(infra.Reader),
		memberships:     repository.NewMembership(infra.Reader),
		createHousehold: usecase.NewCreateHousehold(infra),
		createInvite:    usecase.NewCreateInvite(infra),
		acceptInvite:    usecase.NewAcceptInvite(infra),
		removeMember:    usecase.NewRemoveMember(infra),
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
func (e *env) household(t *testing.T, plan model.Plan) (uuid.UUID, uuid.UUID) {
	t.Helper()

	ownerID := e.user(t)
	out, err := e.createHousehold.Do(e.ctx, usecase.CreateHouseholdInput{UserID: ownerID, HouseholdID: newID()})
	require.NoError(t, err)
	_, err = e.infra.Writer.Exec(t.Context(), "UPDATE households SET plan = $1 WHERE id = $2", plan, out.Household.ID)
	require.NoError(t, err)
	return ownerID, out.Household.ID
}

func (e *env) member(t *testing.T, householdID uuid.UUID) uuid.UUID {
	t.Helper()

	userID := e.user(t)
	_, err := e.infra.Writer.Exec(t.Context(),
		"INSERT INTO memberships (household_id, user_id, role) VALUES ($1, $2, 'member')", householdID, userID)
	require.NoError(t, err)
	return userID
}

func (e *env) invite(t *testing.T, householdID uuid.UUID) string {
	t.Helper()

	h, err := e.households.Find(t.Context(), householdID)
	require.NoError(t, err)
	out, err := e.createInvite.Do(e.ctx, usecase.CreateInviteInput{UserID: h.OwnerID, HouseholdID: householdID})
	require.NoError(t, err)
	return out.Token
}

func (e *env) count(t *testing.T, table string, householdID uuid.UUID) int {
	t.Helper()

	var n int
	require.NoError(t, e.infra.Writer.QueryRow(t.Context(),
		"SELECT count(*) FROM "+table+" WHERE household_id = $1", householdID).Scan(&n))
	return n
}

func contents() model.Contents {
	shopping := "shopping"
	categoryID := newID()
	placeID := newID()
	completedAt := now.Add(-time.Hour)
	return model.Contents{
		Categories: []model.ImportCategory{
			{ID: categoryID, BuiltinKey: &shopping, Icon: "cart", Color: "green"},
		},
		Places: []model.ImportPlace{
			{ID: placeID, Name: "Supermarket", Lat: 35.0, Lng: 139.0, RadiusM: 100, CategoryID: &categoryID},
		},
		Todos: []model.InitialTodo{
			{Todo: model.ImportTodo{ID: newID(), PlaceID: placeID, Title: "Milk"}},
			{Todo: model.ImportTodo{ID: newID(), PlaceID: placeID, Title: "Detergent"}, CompletedAt: &completedAt},
		},
	}
}

func newID() uuid.UUID {
	return uuid.NewV7()
}
