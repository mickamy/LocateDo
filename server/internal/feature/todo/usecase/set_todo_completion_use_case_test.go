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
)

func TestSetTodoCompletion_completeThenReopen(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	setCompletion := usecase.NewSetTodoCompletion(d.Infra())
	h := d.Seeder.Household(t, hmodel.PlanFree)
	memberID := d.Seeder.Member(t, h.ID)
	id := d.Seeder.Todo(t, h.ID, d.Seeder.Place(t, h.ID))

	// act & assert
	require.NoError(t, setCompletion.Do(t.Context(), usecase.SetTodoCompletionInput{
		UserID: memberID, TodoID: id, CompletedAt: &now,
	}))
	got := completedAt(t, d, id, h.ID)
	require.NotNil(t, got)
	assert.True(t, now.Equal(*got))

	require.NoError(t, setCompletion.Do(t.Context(), usecase.SetTodoCompletionInput{UserID: memberID, TodoID: id}))
	assert.Nil(t, completedAt(t, d, id, h.ID))
}

func TestSetTodoCompletion_freeLimitOnReopen(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		plan hmodel.Plan
		want error
	}{
		{name: "free household at the limit", plan: hmodel.PlanFree, want: aerrors.ErrPrecondition},
		{name: "pro household at the limit", plan: hmodel.PlanPro},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			// arrange
			d := tdb.New(t)
			h := d.Seeder.Household(t, tt.plan)
			placeID := d.Seeder.Place(t, h.ID)
			for range hmodel.MaxFreeOpenTodos {
				d.Seeder.Todo(t, h.ID, placeID)
			}
			done := d.Seeder.CompletedTodo(t, h.ID, placeID)

			// act
			err := usecase.NewSetTodoCompletion(d.Infra()).Do(t.Context(), usecase.SetTodoCompletionInput{
				UserID: h.OwnerID, TodoID: done,
			})

			// assert
			if tt.want != nil {
				require.ErrorIs(t, err, tt.want)
				assert.NotNil(t, completedAt(t, d, done, h.ID), "the todo stays completed")
				return
			}
			require.NoError(t, err)
			assert.Nil(t, completedAt(t, d, done, h.ID))
		})
	}
}

func TestSetTodoCompletion_completingIsAlwaysAllowed(t *testing.T) {
	t.Parallel()

	// arrange: a free household already over the limit
	d := tdb.New(t)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	placeID := d.Seeder.Place(t, h.ID)
	var last uuid.UUID
	for range hmodel.MaxFreeOpenTodos + 1 {
		last = d.Seeder.Todo(t, h.ID, placeID)
	}

	// act
	err := usecase.NewSetTodoCompletion(d.Infra()).Do(t.Context(), usecase.SetTodoCompletionInput{
		UserID: h.OwnerID, TodoID: last, CompletedAt: &now,
	})

	// assert
	require.NoError(t, err)
	assert.NotNil(t, completedAt(t, d, last, h.ID))
}

func TestSetTodoCompletion_rejectsOrIgnores(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name    string
		arrange func(t *testing.T, d tdb.DB) usecase.SetTodoCompletionInput
		want    error
	}{
		{
			name: "missing todo is a no-op",
			arrange: func(t *testing.T, d tdb.DB) usecase.SetTodoCompletionInput {
				h := d.Seeder.Household(t, hmodel.PlanFree)
				return usecase.SetTodoCompletionInput{UserID: h.OwnerID, TodoID: uuid.NewV7(), CompletedAt: &now}
			},
		},
		{
			name: "another household's todo is a no-op",
			arrange: func(t *testing.T, d tdb.DB) usecase.SetTodoCompletionInput {
				h := d.Seeder.Household(t, hmodel.PlanFree)
				other := d.Seeder.Household(t, hmodel.PlanFree)
				id := d.Seeder.Todo(t, other.ID, d.Seeder.Place(t, other.ID))
				return usecase.SetTodoCompletionInput{UserID: h.OwnerID, TodoID: id, CompletedAt: &now}
			},
		},
		{
			name: "outsider",
			arrange: func(t *testing.T, d tdb.DB) usecase.SetTodoCompletionInput {
				h := d.Seeder.Household(t, hmodel.PlanFree)
				id := d.Seeder.Todo(t, h.ID, d.Seeder.Place(t, h.ID))
				return usecase.SetTodoCompletionInput{UserID: d.Seeder.User(t), TodoID: id, CompletedAt: &now}
			},
			want: aerrors.ErrPermissionDenied,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			d := tdb.New(t)
			in := tt.arrange(t, d)

			err := usecase.NewSetTodoCompletion(d.Infra()).Do(t.Context(), in)

			if tt.want != nil {
				require.ErrorIs(t, err, tt.want)
				return
			}
			require.NoError(t, err)
		})
	}
}
