package usecase_test

import (
	"testing"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	hmodel "github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/todo/usecase"
	"github.com/mickamy/LocateDo/test/tdb"
	"github.com/mickamy/LocateDo/test/tseed"
)

func TestPutTodo_access(t *testing.T) {
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
				return h.OwnerID, d.Seeder.Household(t, hmodel.PlanPro).ID
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
			h := d.Seeder.Household(t, hmodel.PlanPro)
			memberID := d.Seeder.Member(t, h.ID)
			callerID, target := tt.arrange(t, d, h, memberID)
			td := todoAt(target, d.Seeder.Place(t, target))

			// act
			err := usecase.NewPutTodo(d.Infra()).Do(t.Context(), usecase.PutTodoInput{UserID: callerID, Todo: td})

			// assert
			if tt.want != nil {
				require.ErrorIs(t, err, tt.want)
				assert.Zero(t, d.Seeder.Count(t, "todos", target))
				return
			}
			require.NoError(t, err)
			assert.Equal(t, 1, d.Seeder.Count(t, "todos", target))
		})
	}
}

func TestPutTodo_freeLimit(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name      string
		plan      hmodel.Plan
		open      int
		completed int
		want      error
	}{
		{
			name: "free household below the limit",
			plan: hmodel.PlanFree,
			open: hmodel.MaxFreeOpenTodos - 1,
		},
		{
			name: "free household at the limit",
			plan: hmodel.PlanFree,
			open: hmodel.MaxFreeOpenTodos,
			want: aerrors.ErrPrecondition,
		},
		{
			name:      "completed todos do not count",
			plan:      hmodel.PlanFree,
			completed: hmodel.MaxFreeOpenTodos,
		},
		{
			name: "pro household at the limit",
			plan: hmodel.PlanPro,
			open: hmodel.MaxFreeOpenTodos,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			// arrange
			d := tdb.New(t)
			h := d.Seeder.Household(t, tt.plan)
			placeID := d.Seeder.Place(t, h.ID)
			for range tt.open {
				d.Seeder.Todo(t, h.ID, placeID)
			}
			for range tt.completed {
				d.Seeder.CompletedTodo(t, h.ID, placeID)
			}
			existing := tt.open + tt.completed

			// act
			err := usecase.NewPutTodo(d.Infra()).Do(t.Context(), usecase.PutTodoInput{
				UserID: h.OwnerID, Todo: todoAt(h.ID, placeID),
			})

			// assert
			if tt.want != nil {
				require.ErrorIs(t, err, tt.want)
				assert.Equal(t, existing, d.Seeder.Count(t, "todos", h.ID))
				return
			}
			require.NoError(t, err)
			assert.Equal(t, existing+1, d.Seeder.Count(t, "todos", h.ID))
		})
	}
}

func TestPutTodo_freeHouseholdOverTheLimitKeepsEditing(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	putTodo := usecase.NewPutTodo(d.Infra())
	h := d.Seeder.Household(t, hmodel.PlanFree)
	placeID := d.Seeder.Place(t, h.ID)
	td := todoAt(h.ID, placeID)
	require.NoError(t, putTodo.Do(t.Context(), usecase.PutTodoInput{UserID: h.OwnerID, Todo: td}))
	for range hmodel.MaxFreeOpenTodos {
		d.Seeder.Todo(t, h.ID, placeID)
	}
	td.Title = "Oat milk"

	// act
	err := putTodo.Do(t.Context(), usecase.PutTodoInput{UserID: h.OwnerID, Todo: td})

	// assert
	require.NoError(t, err)
	var title string
	require.NoError(t, d.Writer.QueryRow(t.Context(), "SELECT title FROM todos WHERE id = $1", td.ID).Scan(&title))
	assert.Equal(t, "Oat milk", title)
}

func TestPutTodo_placeGoneIsANoOp(t *testing.T) {
	t.Parallel()

	// arrange: even a free household at the limit answers success
	d := tdb.New(t)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	placeID := d.Seeder.Place(t, h.ID)
	for range hmodel.MaxFreeOpenTodos {
		d.Seeder.Todo(t, h.ID, placeID)
	}

	// act
	err := usecase.NewPutTodo(d.Infra()).Do(t.Context(), usecase.PutTodoInput{
		UserID: h.OwnerID, Todo: todoAt(h.ID, uuid.NewV7()),
	})

	// assert
	require.NoError(t, err)
	assert.Equal(t, hmodel.MaxFreeOpenTodos, d.Seeder.Count(t, "todos", h.ID))
}
