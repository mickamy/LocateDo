package usecase_test

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	hmodel "github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/todo/usecase"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestDeleteTodo(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	deleteTodo := usecase.NewDeleteTodo(d.Infra())
	h := d.Seeder.Household(t, hmodel.PlanFree)
	id := d.Seeder.Todo(t, h.ID, d.Seeder.Place(t, h.ID))

	// act
	err := deleteTodo.Do(t.Context(), usecase.DeleteTodoInput{HouseholdID: h.ID, TodoID: id})
	again := deleteTodo.Do(t.Context(), usecase.DeleteTodoInput{HouseholdID: h.ID, TodoID: id})

	// assert
	require.NoError(t, err)
	require.NoError(t, again, "a retry after the todo is gone succeeds")
	assert.Zero(t, d.Seeder.Count(t, "todos", h.ID))
}

func TestDeleteTodo_anotherHousehold(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	other := d.Seeder.Household(t, hmodel.PlanFree)
	id := d.Seeder.Todo(t, other.ID, d.Seeder.Place(t, other.ID))

	// act
	err := usecase.NewDeleteTodo(d.Infra()).Do(t.Context(), usecase.DeleteTodoInput{HouseholdID: h.ID, TodoID: id})

	// assert
	require.NoError(t, err)
	assert.Equal(t, 1, d.Seeder.Count(t, "todos", other.ID))
}
