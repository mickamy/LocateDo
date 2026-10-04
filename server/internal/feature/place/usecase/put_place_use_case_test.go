package usecase_test

import (
	"testing"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	hmodel "github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/place/fixture"
	"github.com/mickamy/LocateDo/internal/feature/place/usecase"
)

func TestPutPlace_access(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		// arrange returns the caller and the household to write to.
		arrange func(t *testing.T, e *env, ownerID, memberID, householdID uuid.UUID) (uuid.UUID, uuid.UUID)
		want    error
	}{
		{
			name: "owner",
			arrange: func(_ *testing.T, _ *env, ownerID, _, householdID uuid.UUID) (uuid.UUID, uuid.UUID) {
				return ownerID, householdID
			},
		},
		{
			name: "member",
			arrange: func(_ *testing.T, _ *env, _, memberID, householdID uuid.UUID) (uuid.UUID, uuid.UUID) {
				return memberID, householdID
			},
		},
		{
			name: "another household",
			arrange: func(t *testing.T, e *env, ownerID, _, _ uuid.UUID) (uuid.UUID, uuid.UUID) {
				_, other := e.household(t, hmodel.PlanPro)
				return ownerID, other
			},
			want: aerrors.ErrPermissionDenied,
		},
		{
			name: "outsider",
			arrange: func(t *testing.T, e *env, _, _, householdID uuid.UUID) (uuid.UUID, uuid.UUID) {
				return e.user(t), householdID
			},
			want: aerrors.ErrPermissionDenied,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			// arrange
			e := newEnv(t)
			ownerID, householdID := e.household(t, hmodel.PlanPro)
			memberID := e.member(t, householdID)
			callerID, target := tt.arrange(t, e, ownerID, memberID, householdID)
			p := fixture.Place(inHousehold(target))

			// act
			err := e.putPlace.Do(t.Context(), usecase.PutPlaceInput{UserID: callerID, Place: p})

			// assert
			if tt.want != nil {
				require.ErrorIs(t, err, tt.want)
				assert.Zero(t, e.count(t, "places", target))
				return
			}
			require.NoError(t, err)
			exists, err := e.places.Exists(t.Context(), p.ID, target)
			require.NoError(t, err)
			assert.True(t, exists)
		})
	}
}

func TestPutPlace_freeLimit(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name     string
		plan     hmodel.Plan
		existing int
		want     error
	}{
		{
			name:     "free household below the limit",
			plan:     hmodel.PlanFree,
			existing: hmodel.MaxFreePlaces - 1,
		},
		{
			name:     "free household at the limit",
			plan:     hmodel.PlanFree,
			existing: hmodel.MaxFreePlaces,
			want:     aerrors.ErrPrecondition,
		},
		{
			name:     "pro household at the limit",
			plan:     hmodel.PlanPro,
			existing: hmodel.MaxFreePlaces,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			// arrange
			e := newEnv(t)
			ownerID, householdID := e.household(t, tt.plan)
			for range tt.existing {
				e.place(t, householdID)
			}

			// act
			err := e.putPlace.Do(t.Context(), usecase.PutPlaceInput{
				UserID: ownerID, Place: fixture.Place(inHousehold(householdID)),
			})

			// assert
			if tt.want != nil {
				require.ErrorIs(t, err, tt.want)
				assert.Equal(t, tt.existing, e.count(t, "places", householdID))
				return
			}
			require.NoError(t, err)
			assert.Equal(t, tt.existing+1, e.count(t, "places", householdID))
		})
	}
}

func TestPutPlace_freeHouseholdOverTheLimitKeepsEditing(t *testing.T) {
	t.Parallel()

	// arrange
	e := newEnv(t)
	ownerID, householdID := e.household(t, hmodel.PlanFree)
	p := fixture.Place(inHousehold(householdID))
	require.NoError(t, e.putPlace.Do(t.Context(), usecase.PutPlaceInput{UserID: ownerID, Place: p}))
	for range hmodel.MaxFreePlaces {
		e.place(t, householdID)
	}
	p.Name = "Grocery"

	// act
	err := e.putPlace.Do(t.Context(), usecase.PutPlaceInput{UserID: ownerID, Place: p})

	// assert
	require.NoError(t, err)
	var name string
	require.NoError(t, e.infra.Writer.QueryRow(t.Context(), "SELECT name FROM places WHERE id = $1", p.ID).Scan(&name))
	assert.Equal(t, "Grocery", name)
	assert.Equal(t, hmodel.MaxFreePlaces+1, e.count(t, "places", householdID))
}
