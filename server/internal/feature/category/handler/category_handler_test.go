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
	categoryv1 "github.com/mickamy/LocateDo/internal/gen/locatedo/category/v1"
	"github.com/mickamy/LocateDo/internal/gen/locatedo/category/v1/categoryv1connect"
	"github.com/mickamy/LocateDo/internal/server"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestCategory_putThenDelete(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	client := newClient(t, d)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	member := token(t, d.Seeder.Member(t, h.ID))
	input := builtinInput(categoryv1.BuiltinCategory_BUILTIN_CATEGORY_SHOPPING)

	// act & assert: a member adds a built-in
	_, err := client.PutCategory(t.Context(), authed(member, &categoryv1.PutCategoryRequest{
		HouseholdId: h.ID.String(),
		Category:    input,
	}))
	require.NoError(t, err)
	assert.Equal(t, 1, d.Seeder.Count(t, "categories", h.ID))

	// act & assert: renaming it keeps one row
	name := "Costco"
	input.Name = &name
	_, err = client.PutCategory(t.Context(), authed(member, &categoryv1.PutCategoryRequest{
		HouseholdId: h.ID.String(),
		Category:    input,
	}))
	require.NoError(t, err)
	assert.Equal(t, 1, d.Seeder.Count(t, "categories", h.ID))

	// act & assert: deleting twice is fine
	_, err = client.DeleteCategory(t.Context(), authed(member, &categoryv1.DeleteCategoryRequest{Id: input.GetId()}))
	require.NoError(t, err)
	_, err = client.DeleteCategory(t.Context(), authed(member, &categoryv1.DeleteCategoryRequest{Id: input.GetId()}))
	require.NoError(t, err)
	assert.Zero(t, d.Seeder.Count(t, "categories", h.ID))
}

func TestCategory_PutCategory_rejects(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		// arrange returns the caller's token, the household to write to, and the category.
		arrange func(t *testing.T, d tdb.DB) (string, uuid.UUID, *categoryv1.CategoryInput)
		want    connect.Code
	}{
		{
			name: "custom category without a name",
			arrange: func(t *testing.T, d tdb.DB) (string, uuid.UUID, *categoryv1.CategoryInput) {
				h := d.Seeder.Household(t, hmodel.PlanFree)
				return token(t, h.OwnerID), h.ID, builtinInput(categoryv1.BuiltinCategory_BUILTIN_CATEGORY_UNSPECIFIED)
			},
			want: connect.CodeInvalidArgument,
		},
		{
			name: "second built-in with the same key",
			arrange: func(t *testing.T, d tdb.DB) (string, uuid.UUID, *categoryv1.CategoryInput) {
				h := d.Seeder.Household(t, hmodel.PlanFree)
				d.Seeder.BuiltinCategory(t, h.ID, "shopping")
				return token(t, h.OwnerID), h.ID, builtinInput(categoryv1.BuiltinCategory_BUILTIN_CATEGORY_SHOPPING)
			},
			want: connect.CodeAlreadyExists,
		},
		{
			name: "another household",
			arrange: func(t *testing.T, d tdb.DB) (string, uuid.UUID, *categoryv1.CategoryInput) {
				h := d.Seeder.Household(t, hmodel.PlanFree)
				other := d.Seeder.Household(t, hmodel.PlanFree)
				return token(t, h.OwnerID), other.ID, builtinInput(categoryv1.BuiltinCategory_BUILTIN_CATEGORY_WORK)
			},
			want: connect.CodePermissionDenied,
		},
		{
			name: "no token",
			arrange: func(t *testing.T, d tdb.DB) (string, uuid.UUID, *categoryv1.CategoryInput) {
				h := d.Seeder.Household(t, hmodel.PlanFree)
				return "", h.ID, builtinInput(categoryv1.BuiltinCategory_BUILTIN_CATEGORY_WORK)
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
			tok, householdID, input := tt.arrange(t, d)

			// act
			_, err := client.PutCategory(t.Context(), authed(tok, &categoryv1.PutCategoryRequest{
				HouseholdId: householdID.String(),
				Category:    input,
			}))

			// assert
			assert.Equal(t, tt.want, connect.CodeOf(err))
		})
	}
}

func newClient(t *testing.T, d tdb.DB) categoryv1connect.CategoryServiceClient {
	t.Helper()

	lib := di.MustNewLib(di.NewConfig())
	cfg := di.Config{App: config.App{Env: config.EnvTest}}
	handlers := server.NewHandlers(cfg, d.Infra(), lib)
	srv := httptest.NewServer(server.Handler(*handlers))
	t.Cleanup(srv.Close)
	return categoryv1connect.NewCategoryServiceClient(srv.Client(), srv.URL)
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

func builtinInput(b categoryv1.BuiltinCategory) *categoryv1.CategoryInput {
	return &categoryv1.CategoryInput{
		Id:      uuid.NewV7().String(),
		Builtin: b,
		Icon:    "cart",
		Color:   "green",
	}
}
