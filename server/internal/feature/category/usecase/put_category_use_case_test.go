package usecase_test

import (
	"testing"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/category/fixture"
	"github.com/mickamy/LocateDo/internal/feature/category/model"
	"github.com/mickamy/LocateDo/internal/feature/category/usecase"
	hmodel "github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/test/tdb"
	"github.com/mickamy/LocateDo/test/tseed"
)

func TestPutCategory_access(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		// arrange returns the caller and the household to write to.
		arrange func(t *testing.T, d tdb.DB, h tseed.Household, memberID uuid.UUID) (uuid.UUID, uuid.UUID)
		want    error
	}{
		{
			name: "owner",
			arrange: func(_ *testing.T, _ tdb.DB, h tseed.Household, _ uuid.UUID) (uuid.UUID, uuid.UUID) {
				return h.OwnerID, h.ID
			},
		},
		{
			name: "member",
			arrange: func(_ *testing.T, _ tdb.DB, h tseed.Household, memberID uuid.UUID) (uuid.UUID, uuid.UUID) {
				return memberID, h.ID
			},
		},
		{
			name: "another household",
			arrange: func(t *testing.T, d tdb.DB, h tseed.Household, _ uuid.UUID) (uuid.UUID, uuid.UUID) {
				return h.OwnerID, d.Seeder.Household(t, hmodel.PlanFree).ID
			},
			want: aerrors.ErrPermissionDenied,
		},
		{
			name: "outsider",
			arrange: func(t *testing.T, d tdb.DB, h tseed.Household, _ uuid.UUID) (uuid.UUID, uuid.UUID) {
				return d.Seeder.User(t), h.ID
			},
			want: aerrors.ErrPermissionDenied,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			// arrange
			d := tdb.New(t)
			h := d.Seeder.Household(t, hmodel.PlanFree)
			memberID := d.Seeder.Member(t, h.ID)
			callerID, target := tt.arrange(t, d, h, memberID)
			name := "Drugstore"
			c := fixture.Category(func(m *model.Category) { m.HouseholdID = target; m.Name = &name })

			// act
			err := usecase.NewPutCategory(d.Infra()).Do(t.Context(), usecase.PutCategoryInput{UserID: callerID, Category: c})

			// assert
			if tt.want != nil {
				require.ErrorIs(t, err, tt.want)
				assert.Zero(t, d.Seeder.Count(t, "categories", target))
				return
			}
			require.NoError(t, err)
			assert.Equal(t, 1, d.Seeder.Count(t, "categories", target))
		})
	}
}
