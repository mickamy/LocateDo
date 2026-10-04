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
	hmodel "github.com/mickamy/LocateDo/internal/feature/household/model"
	todov1 "github.com/mickamy/LocateDo/internal/gen/locatedo/todo/v1"
	"github.com/mickamy/LocateDo/internal/gen/locatedo/todo/v1/todov1connect"
	"github.com/mickamy/LocateDo/internal/server"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestTodo_putCompleteDelete(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	client := newClient(t, d)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	member := token(t, d.Seeder.Member(t, h.ID))
	input := todoInput(d.Seeder.Place(t, h.ID))

	// act & assert: a member adds a todo
	_, err := client.PutTodo(t.Context(), authed(member, &todov1.PutTodoRequest{
		HouseholdId: h.ID.String(),
		Todo:        input,
	}))
	require.NoError(t, err)
	assert.Equal(t, 1, d.Seeder.Count(t, "todos", h.ID))

	// act & assert: completing, then editing the title, keeps the completion
	_, err = client.SetTodoCompletion(t.Context(), authed(member, &todov1.SetTodoCompletionRequest{
		Id:          input.GetId(),
		CompletedAt: timestamppb.New(time.Now()),
	}))
	require.NoError(t, err)
	input.Title = "Oat milk"
	_, err = client.PutTodo(t.Context(), authed(member, &todov1.PutTodoRequest{
		HouseholdId: h.ID.String(),
		Todo:        input,
	}))
	require.NoError(t, err)
	assert.NotNil(t, completedAt(t, d, input.GetId()))

	// act & assert: reopening clears it
	_, err = client.SetTodoCompletion(t.Context(), authed(member, &todov1.SetTodoCompletionRequest{Id: input.GetId()}))
	require.NoError(t, err)
	assert.Nil(t, completedAt(t, d, input.GetId()))

	// act & assert: deleting twice is fine
	_, err = client.DeleteTodo(t.Context(), authed(member, &todov1.DeleteTodoRequest{Id: input.GetId()}))
	require.NoError(t, err)
	_, err = client.DeleteTodo(t.Context(), authed(member, &todov1.DeleteTodoRequest{Id: input.GetId()}))
	require.NoError(t, err)
	assert.Zero(t, d.Seeder.Count(t, "todos", h.ID))
}

func TestTodo_PutTodo_placeGoneIsANoOp(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	client := newClient(t, d)
	h := d.Seeder.Household(t, hmodel.PlanFree)

	// act
	_, err := client.PutTodo(t.Context(), authed(token(t, h.OwnerID), &todov1.PutTodoRequest{
		HouseholdId: h.ID.String(),
		Todo:        todoInput(uuid.NewV7()),
	}))

	// assert
	require.NoError(t, err)
	assert.Zero(t, d.Seeder.Count(t, "todos", h.ID))
}

func TestTodo_PutTodo_rejects(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		// arrange returns the caller's token, the household to write to, and the todo.
		arrange func(t *testing.T, d tdb.DB) (string, uuid.UUID, *todov1.TodoInput)
		want    connect.Code
	}{
		{
			name: "empty title",
			arrange: func(t *testing.T, d tdb.DB) (string, uuid.UUID, *todov1.TodoInput) {
				h := d.Seeder.Household(t, hmodel.PlanFree)
				input := todoInput(d.Seeder.Place(t, h.ID))
				input.Title = ""
				return token(t, h.OwnerID), h.ID, input
			},
			want: connect.CodeInvalidArgument,
		},
		{
			name: "free household at the open-todo limit",
			arrange: func(t *testing.T, d tdb.DB) (string, uuid.UUID, *todov1.TodoInput) {
				h := d.Seeder.Household(t, hmodel.PlanFree)
				placeID := d.Seeder.Place(t, h.ID)
				for range hmodel.MaxFreeOpenTodos {
					d.Seeder.Todo(t, h.ID, placeID)
				}
				return token(t, h.OwnerID), h.ID, todoInput(placeID)
			},
			want: connect.CodeFailedPrecondition,
		},
		{
			name: "another household",
			arrange: func(t *testing.T, d tdb.DB) (string, uuid.UUID, *todov1.TodoInput) {
				h := d.Seeder.Household(t, hmodel.PlanFree)
				other := d.Seeder.Household(t, hmodel.PlanFree)
				return token(t, h.OwnerID), other.ID, todoInput(d.Seeder.Place(t, other.ID))
			},
			want: connect.CodePermissionDenied,
		},
		{
			name: "no household",
			arrange: func(t *testing.T, d tdb.DB) (string, uuid.UUID, *todov1.TodoInput) {
				h := d.Seeder.Household(t, hmodel.PlanFree)
				return token(t, d.Seeder.User(t)), h.ID, todoInput(d.Seeder.Place(t, h.ID))
			},
			want: connect.CodePermissionDenied,
		},
		{
			name: "no token",
			arrange: func(t *testing.T, d tdb.DB) (string, uuid.UUID, *todov1.TodoInput) {
				h := d.Seeder.Household(t, hmodel.PlanFree)
				return "", h.ID, todoInput(d.Seeder.Place(t, h.ID))
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
			_, err := client.PutTodo(t.Context(), authed(tok, &todov1.PutTodoRequest{
				HouseholdId: householdID.String(),
				Todo:        input,
			}))

			// assert
			assert.Equal(t, tt.want, connect.CodeOf(err))
		})
	}
}

func newClient(t *testing.T, d tdb.DB) todov1connect.TodoServiceClient {
	t.Helper()

	lib := di.MustNewLib(di.NewConfig())
	cfg := di.Config{App: config.App{Env: config.EnvTest}}
	handlers := server.NewHandlers(cfg, d.Infra(), lib)
	srv := httptest.NewServer(server.Handler(*handlers))
	t.Cleanup(srv.Close)
	return todov1connect.NewTodoServiceClient(srv.Client(), srv.URL)
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

func completedAt(t *testing.T, d tdb.DB, id string) *time.Time {
	t.Helper()

	var at *time.Time
	require.NoError(t, d.Writer.QueryRow(t.Context(), "SELECT completed_at FROM todos WHERE id = $1", id).Scan(&at))
	return at
}

func todoInput(placeID uuid.UUID) *todov1.TodoInput {
	return &todov1.TodoInput{
		Id:      uuid.NewV7().String(),
		PlaceId: placeID.String(),
		Title:   "Milk",
	}
}
