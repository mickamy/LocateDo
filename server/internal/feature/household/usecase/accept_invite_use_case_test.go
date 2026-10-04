package usecase_test

import (
	"context"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/household/repository"
	"github.com/mickamy/LocateDo/internal/feature/household/usecase"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestAcceptInvite_newcomer(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	h := d.Seeder.Household(t, model.PlanPro)
	tok := invite(t, d, h)
	inviteeID := d.Seeder.User(t)

	// act
	out, err := usecase.NewAcceptInvite(d.Infra()).Do(fixedClock(t), usecase.AcceptInviteInput{
		UserID: inviteeID, Token: tok,
	})

	// assert
	require.NoError(t, err)
	assert.Equal(t, h.ID, out.Household.ID)
	m, err := repository.NewMembership(d.Reader).FindByUser(t.Context(), inviteeID)
	require.NoError(t, err)
	assert.Equal(t, h.ID, m.HouseholdID)
	assert.Equal(t, model.RoleMember, m.Role)
}

func TestAcceptInvite_bringsSoloHousehold(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	h := d.Seeder.Household(t, model.PlanPro)
	tok := invite(t, d, h)
	inviteeID := d.Seeder.User(t)
	soloID := newID()
	_, err := usecase.NewCreateHousehold(d.Infra()).Do(fixedClock(t), usecase.CreateHouseholdInput{
		UserID: inviteeID, HouseholdID: soloID, Contents: contents(),
	})
	require.NoError(t, err)

	// act
	_, err = usecase.NewAcceptInvite(d.Infra()).Do(fixedClock(t), usecase.AcceptInviteInput{UserID: inviteeID, Token: tok})

	// assert
	require.NoError(t, err)
	assert.Equal(t, 1, d.Seeder.Count(t, "places", h.ID))
	assert.Equal(t, 2, d.Seeder.Count(t, "todos", h.ID))
	_, err = repository.NewHousehold(d.Reader).Find(t.Context(), soloID)
	require.ErrorIs(t, err, aerrors.ErrNotFound)
}

func TestAcceptInvite_sharedHouseholdIsRefused(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	acceptInvite := usecase.NewAcceptInvite(d.Infra())
	h := d.Seeder.Household(t, model.PlanPro)
	tok := invite(t, d, h)
	invitee := d.Seeder.Household(t, model.PlanFree)
	d.Seeder.Member(t, invitee.ID)

	// act
	_, err := acceptInvite.Do(fixedClock(t), usecase.AcceptInviteInput{UserID: invitee.OwnerID, Token: tok})

	// assert
	require.ErrorIs(t, err, aerrors.ErrPrecondition)
	_, err = acceptInvite.Do(fixedClock(t), usecase.AcceptInviteInput{UserID: d.Seeder.User(t), Token: tok})
	require.NoError(t, err, "the refused invite stays unused")
}

func TestAcceptInvite_rejects(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name    string
		arrange func(t *testing.T, d tdb.DB) (context.Context, usecase.AcceptInviteInput)
		want    error
	}{
		{
			name: "expired",
			arrange: func(t *testing.T, d tdb.DB) (context.Context, usecase.AcceptInviteInput) {
				h := d.Seeder.Household(t, model.PlanPro)
				tok := invite(t, d, h)
				later := clock.Set(t.Context(), clock.NewFixed(now.Add(model.InviteTTL)))
				return later, usecase.AcceptInviteInput{UserID: d.Seeder.User(t), Token: tok}
			},
			want: aerrors.ErrPrecondition,
		},
		{
			name: "already used",
			arrange: func(t *testing.T, d tdb.DB) (context.Context, usecase.AcceptInviteInput) {
				h := d.Seeder.Household(t, model.PlanPro)
				tok := invite(t, d, h)
				_, err := usecase.NewAcceptInvite(d.Infra()).Do(fixedClock(t), usecase.AcceptInviteInput{
					UserID: d.Seeder.User(t), Token: tok,
				})
				require.NoError(t, err)
				return fixedClock(t), usecase.AcceptInviteInput{UserID: d.Seeder.User(t), Token: tok}
			},
			want: aerrors.ErrNotFound,
		},
		{
			name: "already a member",
			arrange: func(t *testing.T, d tdb.DB) (context.Context, usecase.AcceptInviteInput) {
				h := d.Seeder.Household(t, model.PlanPro)
				memberID := d.Seeder.Member(t, h.ID)
				return fixedClock(t), usecase.AcceptInviteInput{UserID: memberID, Token: invite(t, d, h)}
			},
			want: aerrors.ErrConflict,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			d := tdb.New(t)
			ctx, in := tt.arrange(t, d)

			_, err := usecase.NewAcceptInvite(d.Infra()).Do(ctx, in)

			require.ErrorIs(t, err, tt.want)
		})
	}
}
