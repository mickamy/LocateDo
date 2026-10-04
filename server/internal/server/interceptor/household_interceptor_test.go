package interceptor_test

import (
	"context"
	"testing"
	"uuid"

	"connectrpc.com/connect"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	hmodel "github.com/mickamy/LocateDo/internal/feature/household/model"
	hrepository "github.com/mickamy/LocateDo/internal/feature/household/repository"
	placev1 "github.com/mickamy/LocateDo/internal/gen/locatedo/place/v1"
	"github.com/mickamy/LocateDo/internal/lib/caller"
	"github.com/mickamy/LocateDo/internal/server/interceptor"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestHousehold(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		// arrange returns the incoming context and the household it should resolve to, if any.
		arrange func(t *testing.T, d tdb.DB) (context.Context, *uuid.UUID)
	}{
		{
			name: "member",
			arrange: func(t *testing.T, d tdb.DB) (context.Context, *uuid.UUID) {
				h := d.Seeder.Household(t, hmodel.PlanFree)
				return caller.Set(t.Context(), d.Seeder.Member(t, h.ID)), &h.ID
			},
		},
		{
			name: "user without a household",
			arrange: func(t *testing.T, d tdb.DB) (context.Context, *uuid.UUID) {
				return caller.Set(t.Context(), d.Seeder.User(t)), nil
			},
		},
		{
			name: "no caller",
			arrange: func(t *testing.T, _ tdb.DB) (context.Context, *uuid.UUID) {
				return t.Context(), nil
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			// arrange
			d := tdb.New(t)
			ctx, want := tt.arrange(t, d)
			var got uuid.UUID
			var resolveErr error
			next := func(ctx context.Context, _ connect.AnyRequest) (connect.AnyResponse, error) {
				got, resolveErr = caller.HouseholdID(ctx)
				return nil, nil //nolint:nilnil // the interceptor passes whatever next returns
			}

			// act
			_, err := interceptor.Household(hrepository.NewMembership(d.Reader))(next)(ctx,
				connect.NewRequest(&placev1.DeletePlaceRequest{}))

			// assert
			require.NoError(t, err)
			if want == nil {
				require.ErrorIs(t, resolveErr, aerrors.ErrPermissionDenied)
				return
			}
			require.NoError(t, resolveErr)
			assert.Equal(t, *want, got)
		})
	}
}
