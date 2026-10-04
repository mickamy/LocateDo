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
	categoryv1 "github.com/mickamy/LocateDo/internal/gen/locatedo/category/v1"
	householdv1 "github.com/mickamy/LocateDo/internal/gen/locatedo/household/v1"
	"github.com/mickamy/LocateDo/internal/gen/locatedo/household/v1/householdv1connect"
	placev1 "github.com/mickamy/LocateDo/internal/gen/locatedo/place/v1"
	todov1 "github.com/mickamy/LocateDo/internal/gen/locatedo/todo/v1"
	"github.com/mickamy/LocateDo/internal/server"
	"github.com/mickamy/LocateDo/test/tinfra"
)

func TestHousehold_createInviteAcceptRemove(t *testing.T) {
	t.Parallel()

	// arrange
	e := newEnv(t)
	owner := e.user(t)
	invitee := e.user(t)
	householdID := uuid.NewV7().String()

	// act & assert: the owner imports what the device built while signed out
	created, err := e.client.CreateHousehold(t.Context(), authed(owner, &householdv1.CreateHouseholdRequest{
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
	assert.Equal(t, 1, e.completedTodos(t, householdID), "only the todo sent with completed_at is completed")

	// act & assert: inviting needs pro
	_, err = e.client.CreateInvite(t.Context(), authed(owner, &householdv1.CreateInviteRequest{HouseholdId: householdID}))
	require.Equal(t, connect.CodeFailedPrecondition, connect.CodeOf(err))
	e.makePro(t, householdID)
	invite, err := e.client.CreateInvite(t.Context(), authed(owner, &householdv1.CreateInviteRequest{
		HouseholdId: householdID,
	}))
	require.NoError(t, err)

	// act & assert: the invitee joins
	joined, err := e.client.AcceptInvite(t.Context(), authed(invitee, &householdv1.AcceptInviteRequest{
		Token: invite.Msg.GetToken(),
	}))
	require.NoError(t, err)
	assert.Equal(t, householdID, joined.Msg.GetHousehold().GetId())
	assert.Equal(t, householdv1.Plan_PLAN_PRO, joined.Msg.GetHousehold().GetPlan())

	// act & assert: the owner removes the invitee
	_, err = e.client.RemoveMember(t.Context(), authed(owner, &householdv1.RemoveMemberRequest{
		HouseholdId: householdID,
		UserId:      invitee.String(),
	}))
	require.NoError(t, err)
}

func TestHousehold_CreateHousehold_unknownCategory(t *testing.T) {
	t.Parallel()

	// arrange
	e := newEnv(t)
	p := place()
	unknown := uuid.NewV7().String()
	p.CategoryId = &unknown

	// act
	_, err := e.client.CreateHousehold(t.Context(), authed(e.user(t), &householdv1.CreateHouseholdRequest{
		Id:     uuid.NewV7().String(),
		Places: []*placev1.PlaceInput{p},
	}))

	// assert
	assert.Equal(t, connect.CodeInvalidArgument, connect.CodeOf(err))
}

func TestHousehold_requiresToken(t *testing.T) {
	t.Parallel()

	e := newEnv(t)

	_, err := e.client.CreateHousehold(t.Context(), connect.NewRequest(&householdv1.CreateHouseholdRequest{
		Id: uuid.NewV7().String(),
	}))

	assert.Equal(t, connect.CodeUnauthenticated, connect.CodeOf(err))
}

type env struct {
	infra  di.Infra
	lib    di.Lib
	client householdv1connect.HouseholdServiceClient
}

func newEnv(t *testing.T) *env {
	t.Helper()

	infra := tinfra.New(t)
	lib := di.MustNewLib(di.NewConfig())
	cfg := di.Config{App: config.App{Env: config.EnvTest}}

	handlers := server.NewHandlers(infra, lib)
	srv := httptest.NewServer(server.Handler(cfg, lib, *handlers))
	t.Cleanup(srv.Close)
	return &env{
		infra:  infra,
		lib:    lib,
		client: householdv1connect.NewHouseholdServiceClient(srv.Client(), srv.URL),
	}
}

// user creates a user and an access token for them.
func (e *env) user(t *testing.T) bearer {
	t.Helper()

	var id uuid.UUID
	require.NoError(t, e.infra.Writer.QueryRow(t.Context(), "INSERT INTO users DEFAULT VALUES RETURNING id").Scan(&id))
	raw, _, err := e.lib.Signer.IssueAccess(id, time.Now())
	require.NoError(t, err)
	return bearer{id: id, token: raw}
}

func (e *env) makePro(t *testing.T, householdID string) {
	t.Helper()

	_, err := e.infra.Writer.Exec(t.Context(), "UPDATE households SET plan = 'pro' WHERE id = $1", householdID)
	require.NoError(t, err)
}

func (e *env) completedTodos(t *testing.T, householdID string) int {
	t.Helper()

	var n int
	require.NoError(t, e.infra.Writer.QueryRow(t.Context(),
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
