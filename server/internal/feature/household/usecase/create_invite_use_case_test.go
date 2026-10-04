package usecase_test

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/household/usecase"
)

func TestCreateInvite(t *testing.T) {
	t.Parallel()

	// arrange
	e := newEnv(t)
	h := e.seed.Household(t, model.PlanPro)

	// act
	out, err := e.createInvite.Do(e.ctx, usecase.CreateInviteInput{UserID: h.OwnerID, HouseholdID: h.ID})

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
				h := e.seed.Household(t, model.PlanFree)
				return usecase.CreateInviteInput{UserID: h.OwnerID, HouseholdID: h.ID}
			},
			want: aerrors.ErrPrecondition,
		},
		{
			name: "not the owner",
			arrange: func(t *testing.T, e *env) usecase.CreateInviteInput {
				householdID := e.seed.Household(t, model.PlanPro).ID
				memberID := e.seed.Member(t, householdID)
				return usecase.CreateInviteInput{UserID: memberID, HouseholdID: householdID}
			},
			want: aerrors.ErrPermissionDenied,
		},
		{
			name: "household is full",
			arrange: func(t *testing.T, e *env) usecase.CreateInviteInput {
				h := e.seed.Household(t, model.PlanPro)
				for range model.MaxMembers - 1 {
					e.seed.Member(t, h.ID)
				}
				return usecase.CreateInviteInput{UserID: h.OwnerID, HouseholdID: h.ID}
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
