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
	householdv1 "github.com/mickamy/LocateDo/internal/gen/locatedo/household/v1"
	syncv1 "github.com/mickamy/LocateDo/internal/gen/locatedo/sync/v1"
	"github.com/mickamy/LocateDo/internal/gen/locatedo/sync/v1/syncv1connect"
	todov1 "github.com/mickamy/LocateDo/internal/gen/locatedo/todo/v1"
	"github.com/mickamy/LocateDo/internal/server"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestSync_firstSyncThenIncremental(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	client := newClient(t, d)
	h := d.Seeder.Household(t, hmodel.PlanPro)
	memberID := d.Seeder.Member(t, h.ID)
	member := token(t, memberID)
	categoryID := d.Seeder.BuiltinCategory(t, h.ID, "shopping")
	placeID := d.Seeder.CategorizedPlace(t, h.ID, categoryID)
	todoID := d.Seeder.Todo(t, h.ID, placeID)
	_, err := d.Writer.Exec(t.Context(), "UPDATE todos SET notify_on = 'departure' WHERE id = $1", todoID)
	require.NoError(t, err)

	// act & assert: the first sync carries one of everything, in version order
	first, err := client.Pull(t.Context(), authed(member, &syncv1.PullRequest{HouseholdId: h.ID.String()}))
	require.NoError(t, err)
	assert.False(t, first.Msg.GetHasMore())
	assert.Equal(t, householdv1.Plan_PLAN_PRO, first.Msg.GetHousehold().GetPlan())
	changes := first.Msg.GetChanges()
	require.Len(t, changes, 5)
	assert.Equal(t, h.OwnerID.String(), changes[0].GetMembership().GetUserId())
	assert.Equal(t, householdv1.Role_ROLE_OWNER, changes[0].GetMembership().GetRole())
	assert.Equal(t, memberID.String(), changes[1].GetMembership().GetUserId())
	assert.Equal(t, categoryv1.BuiltinCategory_BUILTIN_CATEGORY_SHOPPING, changes[2].GetCategory().GetBuiltin())
	assert.Equal(t, categoryID.String(), changes[3].GetPlace().GetCategoryId())
	assert.Equal(t, todoID.String(), changes[4].GetTodo().GetId())
	assert.Nil(t, changes[4].GetTodo().GetCompletedAt())
	assert.Equal(t, todov1.PlaceEvent_PLACE_EVENT_DEPARTURE, changes[4].GetTodo().GetTrigger().GetEvent())

	// act & assert: a deletion arrives as a tombstone on the next pull
	_, err = d.Writer.Exec(t.Context(), "DELETE FROM todos WHERE id = $1", todoID)
	require.NoError(t, err)
	second, err := client.Pull(t.Context(), authed(member, &syncv1.PullRequest{
		HouseholdId: h.ID.String(),
		Cursor:      first.Msg.GetCursor(),
	}))
	require.NoError(t, err)
	require.Len(t, second.Msg.GetChanges(), 1)
	deletion := second.Msg.GetChanges()[0].GetDeletion()
	assert.Equal(t, syncv1.EntityKind_ENTITY_KIND_TODO, deletion.GetKind())
	assert.Equal(t, todoID.String(), deletion.GetId())
	assert.Greater(t, second.Msg.GetCursor(), first.Msg.GetCursor())
}

func TestSync_pages(t *testing.T) {
	t.Parallel()

	// arrange: the owner membership plus four places
	d := tdb.New(t)
	client := newClient(t, d)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	for range 4 {
		d.Seeder.Place(t, h.ID)
	}
	owner := token(t, h.OwnerID)

	// act
	var total int
	var cursor int64
	var pages int
	for {
		res, err := client.Pull(t.Context(), authed(owner, &syncv1.PullRequest{
			HouseholdId: h.ID.String(),
			Cursor:      cursor,
			Limit:       2,
		}))
		require.NoError(t, err)
		pages++
		total += len(res.Msg.GetChanges())
		cursor = res.Msg.GetCursor()
		if !res.Msg.GetHasMore() {
			break
		}
	}

	// assert
	assert.Equal(t, 3, pages)
	assert.Equal(t, 5, total)
	assert.Equal(t, d.Seeder.Version(t, h.ID), cursor)
}

func TestSync_Pull_rejects(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		// arrange returns the caller's token and the request.
		arrange func(t *testing.T, d tdb.DB) (string, *syncv1.PullRequest)
		want    connect.Code
	}{
		{
			name: "another household",
			arrange: func(t *testing.T, d tdb.DB) (string, *syncv1.PullRequest) {
				h := d.Seeder.Household(t, hmodel.PlanFree)
				other := d.Seeder.Household(t, hmodel.PlanFree)
				return token(t, h.OwnerID), &syncv1.PullRequest{HouseholdId: other.ID.String()}
			},
			want: connect.CodePermissionDenied,
		},
		{
			name: "removed member asks for the old household",
			arrange: func(t *testing.T, d tdb.DB) (string, *syncv1.PullRequest) {
				h := d.Seeder.Household(t, hmodel.PlanFree)
				memberID := d.Seeder.Member(t, h.ID)
				_, err := d.Writer.Exec(t.Context(), "DELETE FROM memberships WHERE user_id = $1", memberID)
				require.NoError(t, err)
				return token(t, memberID), &syncv1.PullRequest{HouseholdId: h.ID.String()}
			},
			want: connect.CodePermissionDenied,
		},
		{
			name: "limit above the maximum",
			arrange: func(t *testing.T, d tdb.DB) (string, *syncv1.PullRequest) {
				h := d.Seeder.Household(t, hmodel.PlanFree)
				return token(t, h.OwnerID), &syncv1.PullRequest{HouseholdId: h.ID.String(), Limit: 1001}
			},
			want: connect.CodeInvalidArgument,
		},
		{
			name: "no token",
			arrange: func(t *testing.T, d tdb.DB) (string, *syncv1.PullRequest) {
				h := d.Seeder.Household(t, hmodel.PlanFree)
				return "", &syncv1.PullRequest{HouseholdId: h.ID.String()}
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
			tok, req := tt.arrange(t, d)

			// act
			_, err := client.Pull(t.Context(), authed(tok, req))

			// assert
			assert.Equal(t, tt.want, connect.CodeOf(err))
		})
	}
}

func newClient(t *testing.T, d tdb.DB) syncv1connect.SyncServiceClient {
	t.Helper()

	lib := di.MustNewLib(di.NewConfig())
	cfg := di.Config{App: config.App{Env: config.EnvTest}}
	handlers := server.NewHandlers(cfg, d.Infra(), lib)
	srv := httptest.NewServer(server.Handler(*handlers))
	t.Cleanup(srv.Close)
	return syncv1connect.NewSyncServiceClient(srv.Client(), srv.URL)
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
