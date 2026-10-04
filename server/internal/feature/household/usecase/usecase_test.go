package usecase_test

import (
	"context"
	"testing"
	"time"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/household/repository"
	"github.com/mickamy/LocateDo/internal/feature/household/usecase"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/test/tinfra"
)

var now = time.Date(2026, 10, 3, 12, 0, 0, 0, time.UTC)

func TestCreateHousehold_imports(t *testing.T) {
	t.Parallel()

	// arrange
	e := newEnv(t)
	userID := e.user(t)
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
	assert.Equal(t, 1, e.count(t, "categories", householdID))
	assert.Equal(t, 1, e.count(t, "places", householdID))
	assert.Equal(t, 2, e.count(t, "todos", householdID))
}

func TestCreateHousehold_retryIsIdempotent(t *testing.T) {
	t.Parallel()

	// arrange
	e := newEnv(t)
	in := usecase.CreateHouseholdInput{UserID: e.user(t), HouseholdID: newID(), Contents: contents()}
	first, err := e.createHousehold.Do(e.ctx, in)
	require.NoError(t, err)

	// act
	second, err := e.createHousehold.Do(e.ctx, in)

	// assert
	require.NoError(t, err)
	assert.Equal(t, first.Household, second.Household)
	assert.Equal(t, 1, e.count(t, "places", in.HouseholdID))
}

func TestCreateHousehold_alreadyInAnother(t *testing.T) {
	t.Parallel()

	// arrange
	e := newEnv(t)
	userID := e.user(t)
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
		UserID: e.user(t), HouseholdID: householdID, Contents: c,
	})

	// assert
	require.ErrorIs(t, err, aerrors.ErrInvalidArgument)
	_, err = e.households.Find(t.Context(), householdID)
	require.ErrorIs(t, err, aerrors.ErrNotFound, "nothing is left behind")
}

func TestCreateInvite(t *testing.T) {
	t.Parallel()

	// arrange
	e := newEnv(t)
	ownerID, householdID := e.household(t, model.PlanPro)

	// act
	out, err := e.createInvite.Do(e.ctx, usecase.CreateInviteInput{UserID: ownerID, HouseholdID: householdID})

	// assert
	require.NoError(t, err)
	assert.NotEmpty(t, out.Token)
	assert.Equal(t, now.Add(model.InviteTTL), out.ExpiresAt)
}

func TestCreateInvite_rejects(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name    string
		arrange func(t *testing.T, e *env) usecase.CreateInviteInput
		want    error
	}{
		{
			name: "free plan",
			arrange: func(t *testing.T, e *env) usecase.CreateInviteInput {
				ownerID, householdID := e.household(t, model.PlanFree)
				return usecase.CreateInviteInput{UserID: ownerID, HouseholdID: householdID}
			},
			want: aerrors.ErrPrecondition,
		},
		{
			name: "not the owner",
			arrange: func(t *testing.T, e *env) usecase.CreateInviteInput {
				_, householdID := e.household(t, model.PlanPro)
				memberID := e.member(t, householdID)
				return usecase.CreateInviteInput{UserID: memberID, HouseholdID: householdID}
			},
			want: aerrors.ErrPermissionDenied,
		},
		{
			name: "household is full",
			arrange: func(t *testing.T, e *env) usecase.CreateInviteInput {
				ownerID, householdID := e.household(t, model.PlanPro)
				for range model.MaxMembers - 1 {
					e.member(t, householdID)
				}
				return usecase.CreateInviteInput{UserID: ownerID, HouseholdID: householdID}
			},
			want: aerrors.ErrPrecondition,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			e := newEnv(t)
			in := tt.arrange(t, e)

			_, err := e.createInvite.Do(e.ctx, in)

			require.ErrorIs(t, err, tt.want)
		})
	}
}

func TestAcceptInvite_newcomer(t *testing.T) {
	t.Parallel()

	// arrange
	e := newEnv(t)
	_, householdID := e.household(t, model.PlanPro)
	tok := e.invite(t, householdID)
	inviteeID := e.user(t)

	// act
	out, err := e.acceptInvite.Do(e.ctx, usecase.AcceptInviteInput{UserID: inviteeID, Token: tok})

	// assert
	require.NoError(t, err)
	assert.Equal(t, householdID, out.Household.ID)
	m, err := e.memberships.FindByUser(t.Context(), inviteeID)
	require.NoError(t, err)
	assert.Equal(t, householdID, m.HouseholdID)
	assert.Equal(t, model.RoleMember, m.Role)
}

func TestAcceptInvite_bringsSoloHousehold(t *testing.T) {
	t.Parallel()

	// arrange
	e := newEnv(t)
	_, householdID := e.household(t, model.PlanPro)
	tok := e.invite(t, householdID)
	inviteeID := e.user(t)
	soloID := newID()
	_, err := e.createHousehold.Do(e.ctx, usecase.CreateHouseholdInput{
		UserID: inviteeID, HouseholdID: soloID, Contents: contents(),
	})
	require.NoError(t, err)

	// act
	_, err = e.acceptInvite.Do(e.ctx, usecase.AcceptInviteInput{UserID: inviteeID, Token: tok})

	// assert
	require.NoError(t, err)
	assert.Equal(t, 1, e.count(t, "places", householdID))
	assert.Equal(t, 2, e.count(t, "todos", householdID))
	_, err = e.households.Find(t.Context(), soloID)
	require.ErrorIs(t, err, aerrors.ErrNotFound)
}

func TestAcceptInvite_sharedHouseholdIsRefused(t *testing.T) {
	t.Parallel()

	// arrange
	e := newEnv(t)
	_, householdID := e.household(t, model.PlanPro)
	tok := e.invite(t, householdID)
	inviteeID, inviteeHousehold := e.household(t, model.PlanFree)
	e.member(t, inviteeHousehold)

	// act
	_, err := e.acceptInvite.Do(e.ctx, usecase.AcceptInviteInput{UserID: inviteeID, Token: tok})

	// assert
	require.ErrorIs(t, err, aerrors.ErrPrecondition)
	_, err = e.acceptInvite.Do(e.ctx, usecase.AcceptInviteInput{UserID: e.user(t), Token: tok})
	require.NoError(t, err, "the refused invite stays unused")
}

func TestAcceptInvite_rejects(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name    string
		arrange func(t *testing.T, e *env) (context.Context, usecase.AcceptInviteInput)
		want    error
	}{
		{
			name: "expired",
			arrange: func(t *testing.T, e *env) (context.Context, usecase.AcceptInviteInput) {
				_, householdID := e.household(t, model.PlanPro)
				tok := e.invite(t, householdID)
				later := clock.Set(t.Context(), clock.NewFixed(now.Add(model.InviteTTL)))
				return later, usecase.AcceptInviteInput{UserID: e.user(t), Token: tok}
			},
			want: aerrors.ErrPrecondition,
		},
		{
			name: "already used",
			arrange: func(t *testing.T, e *env) (context.Context, usecase.AcceptInviteInput) {
				_, householdID := e.household(t, model.PlanPro)
				tok := e.invite(t, householdID)
				_, err := e.acceptInvite.Do(e.ctx, usecase.AcceptInviteInput{UserID: e.user(t), Token: tok})
				require.NoError(t, err)
				return e.ctx, usecase.AcceptInviteInput{UserID: e.user(t), Token: tok}
			},
			want: aerrors.ErrNotFound,
		},
		{
			name: "already a member",
			arrange: func(t *testing.T, e *env) (context.Context, usecase.AcceptInviteInput) {
				_, householdID := e.household(t, model.PlanPro)
				memberID := e.member(t, householdID)
				return e.ctx, usecase.AcceptInviteInput{UserID: memberID, Token: e.invite(t, householdID)}
			},
			want: aerrors.ErrConflict,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			e := newEnv(t)
			ctx, in := tt.arrange(t, e)

			_, err := e.acceptInvite.Do(ctx, in)

			require.ErrorIs(t, err, tt.want)
		})
	}
}

func TestRemoveMember(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		// arrange returns the caller and the user to remove.
		arrange func(t *testing.T, e *env, ownerID, memberID uuid.UUID) (uuid.UUID, uuid.UUID)
		want    error
	}{
		{
			name: "owner removes a member",
			arrange: func(_ *testing.T, _ *env, ownerID, memberID uuid.UUID) (uuid.UUID, uuid.UUID) {
				return ownerID, memberID
			},
		},
		{
			name: "member leaves",
			arrange: func(_ *testing.T, _ *env, _, memberID uuid.UUID) (uuid.UUID, uuid.UUID) {
				return memberID, memberID
			},
		},
		{
			name: "owner cannot leave",
			arrange: func(_ *testing.T, _ *env, ownerID, _ uuid.UUID) (uuid.UUID, uuid.UUID) {
				return ownerID, ownerID
			},
			want: aerrors.ErrPrecondition,
		},
		{
			name: "member cannot remove others",
			arrange: func(_ *testing.T, _ *env, ownerID, memberID uuid.UUID) (uuid.UUID, uuid.UUID) {
				return memberID, ownerID
			},
			want: aerrors.ErrPermissionDenied,
		},
		{
			name: "outsider",
			arrange: func(t *testing.T, e *env, _, memberID uuid.UUID) (uuid.UUID, uuid.UUID) {
				return e.user(t), memberID
			},
			want: aerrors.ErrPermissionDenied,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			// arrange
			e := newEnv(t)
			ownerID, householdID := e.household(t, model.PlanPro)
			memberID := e.member(t, householdID)
			callerID, userID := tt.arrange(t, e, ownerID, memberID)

			// act
			err := e.removeMember.Do(e.ctx, usecase.RemoveMemberInput{
				CallerID: callerID, HouseholdID: householdID, UserID: userID,
			})

			// assert
			if tt.want != nil {
				require.ErrorIs(t, err, tt.want)
				return
			}
			require.NoError(t, err)
			_, err = e.memberships.FindByUser(t.Context(), userID)
			require.ErrorIs(t, err, aerrors.ErrNotFound)
		})
	}
}

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
