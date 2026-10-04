package usecase_test

import (
	"context"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/household/usecase"
	"github.com/mickamy/LocateDo/internal/lib/clock"
)

func TestAcceptInvite_newcomer(t *testing.T) {
	t.Parallel()

	// arrange
	e := newEnv(t)
	householdID := e.seed.Household(t, model.PlanPro).ID
	tok := e.invite(t, householdID)
	inviteeID := e.seed.User(t)

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
	householdID := e.seed.Household(t, model.PlanPro).ID
	tok := e.invite(t, householdID)
	inviteeID := e.seed.User(t)
	soloID := newID()
	_, err := e.createHousehold.Do(e.ctx, usecase.CreateHouseholdInput{
		UserID: inviteeID, HouseholdID: soloID, Contents: contents(),
	})
	require.NoError(t, err)

	// act
	_, err = e.acceptInvite.Do(e.ctx, usecase.AcceptInviteInput{UserID: inviteeID, Token: tok})

	// assert
	require.NoError(t, err)
	assert.Equal(t, 1, e.seed.Count(t, "places", householdID))
	assert.Equal(t, 2, e.seed.Count(t, "todos", householdID))
	_, err = e.households.Find(t.Context(), soloID)
	require.ErrorIs(t, err, aerrors.ErrNotFound)
}

func TestAcceptInvite_sharedHouseholdIsRefused(t *testing.T) {
	t.Parallel()

	// arrange
	e := newEnv(t)
	householdID := e.seed.Household(t, model.PlanPro).ID
	tok := e.invite(t, householdID)
	invitee := e.seed.Household(t, model.PlanFree)
	e.seed.Member(t, invitee.ID)

	// act
	_, err := e.acceptInvite.Do(e.ctx, usecase.AcceptInviteInput{UserID: invitee.OwnerID, Token: tok})

	// assert
	require.ErrorIs(t, err, aerrors.ErrPrecondition)
	_, err = e.acceptInvite.Do(e.ctx, usecase.AcceptInviteInput{UserID: e.seed.User(t), Token: tok})
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
				householdID := e.seed.Household(t, model.PlanPro).ID
				tok := e.invite(t, householdID)
				later := clock.Set(t.Context(), clock.NewFixed(now.Add(model.InviteTTL)))
				return later, usecase.AcceptInviteInput{UserID: e.seed.User(t), Token: tok}
			},
			want: aerrors.ErrPrecondition,
		},
		{
			name: "already used",
			arrange: func(t *testing.T, e *env) (context.Context, usecase.AcceptInviteInput) {
				householdID := e.seed.Household(t, model.PlanPro).ID
				tok := e.invite(t, householdID)
				_, err := e.acceptInvite.Do(e.ctx, usecase.AcceptInviteInput{UserID: e.seed.User(t), Token: tok})
				require.NoError(t, err)
				return e.ctx, usecase.AcceptInviteInput{UserID: e.seed.User(t), Token: tok}
			},
			want: aerrors.ErrNotFound,
		},
		{
			name: "already a member",
			arrange: func(t *testing.T, e *env) (context.Context, usecase.AcceptInviteInput) {
				householdID := e.seed.Household(t, model.PlanPro).ID
				memberID := e.seed.Member(t, householdID)
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
