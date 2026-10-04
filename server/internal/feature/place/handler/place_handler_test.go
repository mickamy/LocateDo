package handler_test

import (
	"net/http/httptest"
	"testing"
	"time"
	"uuid"

	"connectrpc.com/connect"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/config"
	"github.com/mickamy/LocateDo/internal/di"
	hmodel "github.com/mickamy/LocateDo/internal/feature/household/model"
	placev1 "github.com/mickamy/LocateDo/internal/gen/locatedo/place/v1"
	"github.com/mickamy/LocateDo/internal/gen/locatedo/place/v1/placev1connect"
	"github.com/mickamy/LocateDo/internal/server"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestPlace_putThenDelete(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	client := newClient(t, d)
	h := d.Seeder.Household(t, hmodel.PlanPro)
	member := token(t, d.Seeder.Member(t, h.ID))
	input := placeInput()

	// act & assert: a member adds a place
	_, err := client.PutPlace(t.Context(), authed(member, &placev1.PutPlaceRequest{
		HouseholdId: h.ID.String(),
		Place:       input,
	}))
	require.NoError(t, err)
	assert.Equal(t, 1, d.Seeder.Count(t, "places", h.ID))

	// act & assert: sending the same id again overwrites instead of adding
	input.Name = "Grocery"
	_, err = client.PutPlace(t.Context(), authed(member, &placev1.PutPlaceRequest{
		HouseholdId: h.ID.String(),
		Place:       input,
	}))
	require.NoError(t, err)
	assert.Equal(t, 1, d.Seeder.Count(t, "places", h.ID))

	// act & assert: deleting twice is fine
	_, err = client.DeletePlace(t.Context(), authed(member, &placev1.DeletePlaceRequest{Id: input.GetId()}))
	require.NoError(t, err)
	_, err = client.DeletePlace(t.Context(), authed(member, &placev1.DeletePlaceRequest{Id: input.GetId()}))
	require.NoError(t, err)
	assert.Zero(t, d.Seeder.Count(t, "places", h.ID))
}

func TestPlace_PutPlace_rejects(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		// arrange returns the caller's token and the household to write to.
		arrange func(t *testing.T, d tdb.DB) (string, uuid.UUID)
		want    connect.Code
	}{
		{
			name: "free household at the limit",
			arrange: func(t *testing.T, d tdb.DB) (string, uuid.UUID) {
				h := d.Seeder.Household(t, hmodel.PlanFree)
				for range hmodel.MaxFreePlaces {
					d.Seeder.Place(t, h.ID)
				}
				return token(t, h.OwnerID), h.ID
			},
			want: connect.CodeFailedPrecondition,
		},
		{
			name: "another household",
			arrange: func(t *testing.T, d tdb.DB) (string, uuid.UUID) {
				h := d.Seeder.Household(t, hmodel.PlanPro)
				return token(t, h.OwnerID), d.Seeder.Household(t, hmodel.PlanPro).ID
			},
			want: connect.CodePermissionDenied,
		},
		{
			name: "no token",
			arrange: func(t *testing.T, d tdb.DB) (string, uuid.UUID) {
				return "", d.Seeder.Household(t, hmodel.PlanPro).ID
			},
			want: connect.CodeUnauthenticated,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			// arrange
			d := tdb.New(t)
			client := newClient(t, d)
			tok, householdID := tt.arrange(t, d)

			// act
			_, err := client.PutPlace(t.Context(), authed(tok, &placev1.PutPlaceRequest{
				HouseholdId: householdID.String(),
				Place:       placeInput(),
			}))

			// assert
			assert.Equal(t, tt.want, connect.CodeOf(err))
		})
	}
}

func newClient(t *testing.T, d tdb.DB) placev1connect.PlaceServiceClient {
	t.Helper()

	lib := di.MustNewLib(di.NewConfig())
	cfg := di.Config{App: config.App{Env: config.EnvTest}}
	handlers := server.NewHandlers(cfg, d.Infra(), lib)
	srv := httptest.NewServer(server.Handler(*handlers))
	t.Cleanup(srv.Close)
	return placev1connect.NewPlaceServiceClient(srv.Client(), srv.URL)
}

func token(t *testing.T, userID uuid.UUID) string {
	t.Helper()

	raw, _, err := di.MustNewLib(di.NewConfig()).Signer.IssueAccess(userID, time.Now())
	require.NoError(t, err)
	return raw
}

func authed[T any](token string, msg *T) *connect.Request[T] {
	req := connect.NewRequest(msg)
	if token != "" {
		req.Header().Set("Authorization", "Bearer "+token)
	}
	return req
}

func placeInput() *placev1.PlaceInput {
	return &placev1.PlaceInput{
		Id:      uuid.NewV7().String(),
		Name:    "Supermarket",
		Lat:     35.0,
		Lng:     139.0,
		RadiusM: 100,
	}
}
