package handler_test

import (
	"net/http/httptest"
	"testing"
	"time"
	"uuid"

	"connectrpc.com/connect"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"google.golang.org/protobuf/types/known/timestamppb"

	"github.com/mickamy/LocateDo/config"
	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/feature/household/model"
	categoryv1 "github.com/mickamy/LocateDo/internal/gen/locatedo/category/v1"
	householdv1 "github.com/mickamy/LocateDo/internal/gen/locatedo/household/v1"
	"github.com/mickamy/LocateDo/internal/gen/locatedo/household/v1/householdv1connect"
	placev1 "github.com/mickamy/LocateDo/internal/gen/locatedo/place/v1"
	syncv1 "github.com/mickamy/LocateDo/internal/gen/locatedo/sync/v1"
	"github.com/mickamy/LocateDo/internal/gen/locatedo/sync/v1/syncv1connect"
	todov1 "github.com/mickamy/LocateDo/internal/gen/locatedo/todo/v1"
	"github.com/mickamy/LocateDo/internal/server"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestHousehold_createInviteAcceptRemove(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	client := newClient(t, d)
	owner := user(t, d)
	invitee := user(t, d)
	householdID := uuid.NewV7().String()

	// act & assert: the owner imports what the device built while signed out
	created, err := client.CreateHousehold(t.Context(), authed(owner, &householdv1.CreateHouseholdRequest{
		Id:         householdID,
		Categories: []*categoryv1.CategoryInput{category()},
		Places:     []*placev1.PlaceInput{place()},
		Todos: []*householdv1.InitialTodo{
			{Todo: todo("Milk")},
			{Todo: todo("Detergent"), CompletedAt: timestamppb.New(time.Now().Add(-time.Hour))},
		},
	}))
	require.NoError(t, err)
	assert.Equal(t, householdID, created.Msg.GetHousehold().GetId())
	assert.Equal(t, householdv1.Plan_PLAN_FREE, created.Msg.GetHousehold().GetPlan())
	assert.Zero(t, created.Msg.GetCursor())
	pulled, err := newSyncClient(t, d).Pull(t.Context(), authed(owner, &syncv1.PullRequest{
		HouseholdId: householdID,
		Cursor:      created.Msg.GetCursor(),
	}))
	require.NoError(t, err)
	require.NotEmpty(t, pulled.Msg.GetChanges())
	membership := pulled.Msg.GetChanges()[0].GetMembership()
	assert.Equal(t, owner.String(), membership.GetUserId(), "the first pull carries the owner membership")
	assert.Equal(t, householdv1.Role_ROLE_OWNER, membership.GetRole())
	assert.Equal(t, 1, completedTodos(t, d, householdID), "only the todo sent with completed_at is completed")

	// act & assert: inviting needs pro
	_, err = client.CreateInvite(t.Context(), authed(owner, &householdv1.CreateInviteRequest{HouseholdId: householdID}))
	require.Equal(t, connect.CodeFailedPrecondition, connect.CodeOf(err))
	d.Seeder.SetPlan(t, uuid.MustParse(householdID), model.PlanPro)
	invite, err := client.CreateInvite(t.Context(), authed(owner, &householdv1.CreateInviteRequest{
		HouseholdId: householdID,
	}))
	require.NoError(t, err)

	// act & assert: the invitee joins
	joined, err := client.AcceptInvite(t.Context(), authed(invitee, &householdv1.AcceptInviteRequest{
		Token: invite.Msg.GetToken(),
	}))
	require.NoError(t, err)
	assert.Equal(t, householdID, joined.Msg.GetHousehold().GetId())
	assert.Equal(t, householdv1.Plan_PLAN_PRO, joined.Msg.GetHousehold().GetPlan())

	// act & assert: the owner removes the invitee
	_, err = client.RemoveMember(t.Context(), authed(owner, &householdv1.RemoveMemberRequest{
		HouseholdId: householdID,
		UserId:      invitee.String(),
	}))
	require.NoError(t, err)
}

func TestHousehold_CreateHousehold_unknownCategory(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	client := newClient(t, d)
	p := place()
	unknown := uuid.NewV7().String()
	p.CategoryId = &unknown

	// act
	_, err := client.CreateHousehold(t.Context(), authed(user(t, d), &householdv1.CreateHouseholdRequest{
		Id:     uuid.NewV7().String(),
		Places: []*placev1.PlaceInput{p},
	}))

	// assert
	assert.Equal(t, connect.CodeInvalidArgument, connect.CodeOf(err))
}

func TestHousehold_requiresToken(t *testing.T) {
	t.Parallel()

	d := tdb.New(t)
	client := newClient(t, d)

	_, err := client.CreateHousehold(t.Context(), connect.NewRequest(&householdv1.CreateHouseholdRequest{
		Id: uuid.NewV7().String(),
	}))

	assert.Equal(t, connect.CodeUnauthenticated, connect.CodeOf(err))
}

func newClient(t *testing.T, d tdb.DB) householdv1connect.HouseholdServiceClient {
	t.Helper()

	srv := newServer(t, d)
	return householdv1connect.NewHouseholdServiceClient(srv.Client(), srv.URL)
}

func newSyncClient(t *testing.T, d tdb.DB) syncv1connect.SyncServiceClient {
	t.Helper()

	srv := newServer(t, d)
	return syncv1connect.NewSyncServiceClient(srv.Client(), srv.URL)
}

func newServer(t *testing.T, d tdb.DB) *httptest.Server {
	t.Helper()

	lib := di.MustNewLib(di.NewConfig())
	cfg := di.Config{App: config.App{Env: config.EnvTest}}
	handlers := server.NewHandlers(cfg, d.Infra(), lib)
	srv := httptest.NewServer(server.Handler(*handlers))
	t.Cleanup(srv.Close)
	return srv
}

// user creates a user and an access token for them.
func user(t *testing.T, d tdb.DB) bearer {
	t.Helper()

	id := d.Seeder.User(t)
	raw, _, err := di.MustNewLib(di.NewConfig()).Signer.IssueAccess(id, time.Now())
	require.NoError(t, err)
	return bearer{id: id, token: raw}
}

func completedTodos(t *testing.T, d tdb.DB, householdID string) int {
	t.Helper()

	var n int
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"SELECT count(*) FROM todos WHERE household_id = $1 AND completed_at IS NOT NULL", householdID).Scan(&n))
	return n
}

type bearer struct {
	id    uuid.UUID
	token string
}

func (b bearer) String() string {
	return b.id.String()
}

func authed[T any](b bearer, msg *T) *connect.Request[T] {
	req := connect.NewRequest(msg)
	req.Header().Set("Authorization", "Bearer "+b.token)
	return req
}

var (
	categoryID = uuid.NewV7().String()
	placeID    = uuid.NewV7().String()
)

func category() *categoryv1.CategoryInput {
	return &categoryv1.CategoryInput{
		Id:      categoryID,
		Builtin: categoryv1.BuiltinCategory_BUILTIN_CATEGORY_SHOPPING,
		Icon:    "cart",
		Color:   "green",
	}
}

func place() *placev1.PlaceInput {
	return &placev1.PlaceInput{
		Id:         placeID,
		Name:       "Supermarket",
		Lat:        35.0,
		Lng:        139.0,
		RadiusM:    100,
		CategoryId: &categoryID,
	}
}

func todo(title string) *todov1.TodoInput {
	return &todov1.TodoInput{Id: uuid.NewV7().String(), PlaceId: placeID, Title: title}
}
