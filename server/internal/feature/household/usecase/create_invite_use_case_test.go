package usecase_test

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/household/usecase"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestCreateInvite(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	h := d.Seeder.Household(t, model.PlanPro)

	// act
	out, err := usecase.NewCreateInvite(d.Infra()).Do(fixedClock(t), usecase.CreateInviteInput{
		UserID: h.OwnerID, HouseholdID: h.ID,
	})

	// assert
	require.NoError(t, err)
	assert.NotEmpty(t, out.Token)
	assert.Equal(t, now.Add(model.InviteTTL), out.ExpiresAt)
}

func TestCreateInvite_rejects(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name    string
		arrange func(t *testing.T, d tdb.DB) usecase.CreateInviteInput
		want    error
	}{
		{
			name: "free plan",
			arrange: func(t *testing.T, d tdb.DB) usecase.CreateInviteInput {
				h := d.Seeder.Household(t, model.PlanFree)
				return usecase.CreateInviteInput{UserID: h.OwnerID, HouseholdID: h.ID}
			},
			want: aerrors.ErrPrecondition,
		},
		{
			name: "not the owner",
			arrange: func(t *testing.T, d tdb.DB) usecase.CreateInviteInput {
				h := d.Seeder.Household(t, model.PlanPro)
				return usecase.CreateInviteInput{UserID: d.Seeder.Member(t, h.ID), HouseholdID: h.ID}
			},
			want: aerrors.ErrPermissionDenied,
		},
		{
			name: "household is full",
			arrange: func(t *testing.T, d tdb.DB) usecase.CreateInviteInput {
				h := d.Seeder.Household(t, model.PlanPro)
				for range model.MaxMembers - 1 {
					d.Seeder.Member(t, h.ID)
				}
				return usecase.CreateInviteInput{UserID: h.OwnerID, HouseholdID: h.ID}
			},
			want: aerrors.ErrPrecondition,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			d := tdb.New(t)
			in := tt.arrange(t, d)

			_, err := usecase.NewCreateInvite(d.Infra()).Do(fixedClock(t), in)

			require.ErrorIs(t, err, tt.want)
		})
	}
}
