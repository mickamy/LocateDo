package repository_test

import (
	"testing"
	"time"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	hmodel "github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/todo/model"
	"github.com/mickamy/LocateDo/internal/feature/todo/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/test/tdb"
)

var now = time.Date(2026, 10, 4, 12, 0, 0, 0, time.UTC)

func TestTodo_Upsert_insertThenUpdateKeepsCompletion(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	todos := repository.NewTodo(d.Reader)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	memberID := d.Seeder.Member(t, h.ID)
	placeID := d.Seeder.Place(t, h.ID)
	otherPlace := d.Seeder.Place(t, h.ID)
	td := model.Todo{ID: uuid.NewV7(), HouseholdID: h.ID, PlaceID: placeID, Title: "Milk", AssigneeID: &memberID}

	// act
	var written bool
	d.InTx(t, func(tx tx.Tx) {
		var err error
		written, err = todos.Bind(tx).Upsert(t.Context(), td)
		require.NoError(t, err)
		require.NoError(t, todos.Bind(tx).SetCompletion(t.Context(), td.ID, h.ID, &now))
	})
	inserted, err := todos.Find(t.Context(), td.ID, h.ID)
	require.NoError(t, err)

	td.Title = "Oat milk"
	td.PlaceID = otherPlace
	td.AssigneeID = nil
	d.InTx(t, func(tx tx.Tx) {
		_, err := todos.Bind(tx).Upsert(t.Context(), td)
		require.NoError(t, err)
	})
	updated, err := todos.Find(t.Context(), td.ID, h.ID)
	require.NoError(t, err)

	// assert
	assert.True(t, written)
	assert.Equal(t, &memberID, inserted.AssigneeID)
	assert.Equal(t, "Oat milk", updated.Title)
	assert.Equal(t, otherPlace, updated.PlaceID)
	assert.Nil(t, updated.AssigneeID)
	require.NotNil(t, updated.CompletedAt, "an edit never undoes a completion")
	assert.True(t, now.Equal(*updated.CompletedAt))
}

func TestTodo_Upsert_placeNotInHousehold(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	todos := repository.NewTodo(d.Reader)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	elsewhere := d.Seeder.Place(t, d.Seeder.Household(t, hmodel.PlanFree).ID)

	tests := map[string]uuid.UUID{
		"deleted place":             uuid.NewV7(),
		"another household's place": elsewhere,
	}
	for name, placeID := range tests {
		t.Run(name, func(t *testing.T) {
			t.Parallel()

			// act
			var written bool
			d.InTx(t, func(tx tx.Tx) {
				var err error
				written, err = todos.Bind(tx).Upsert(t.Context(), model.Todo{
					ID: uuid.NewV7(), HouseholdID: h.ID, PlaceID: placeID, Title: "Milk",
				})
				require.NoError(t, err)
			})

			// assert
			assert.False(t, written)
			assert.Zero(t, d.Seeder.Count(t, "todos", h.ID))
		})
	}
}

func TestTodo_Upsert_assigneeOutsideHouseholdBecomesNull(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	todos := repository.NewTodo(d.Reader)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	placeID := d.Seeder.Place(t, h.ID)
	stranger := d.Seeder.User(t)
	id := uuid.NewV7()

	// act
	d.InTx(t, func(tx tx.Tx) {
		_, err := todos.Bind(tx).Upsert(t.Context(), model.Todo{
			ID: id, HouseholdID: h.ID, PlaceID: placeID, Title: "Milk", AssigneeID: &stranger,
		})
		require.NoError(t, err)
	})

	// assert
	got, err := todos.Find(t.Context(), id, h.ID)
	require.NoError(t, err)
	assert.Nil(t, got.AssigneeID)
}

func TestTodo_Upsert_idInAnotherHousehold(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	todos := repository.NewTodo(d.Reader)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	placeID := d.Seeder.Place(t, h.ID)
	other := d.Seeder.Household(t, hmodel.PlanFree)
	taken := d.Seeder.Todo(t, other.ID, d.Seeder.Place(t, other.ID))

	// act
	err := d.Transactor.WithTx(t.Context(), func(tx tx.Tx) error {
		_, err := todos.Bind(tx).Upsert(t.Context(), model.Todo{
			ID: taken, HouseholdID: h.ID, PlaceID: placeID, Title: "Milk",
		})
		return err
	})

	// assert
	require.ErrorIs(t, err, aerrors.ErrConflict)
	_, err = todos.Find(t.Context(), taken, h.ID)
	require.ErrorIs(t, err, aerrors.ErrNotFound)
}

func TestTodo_CountOpen(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	todos := repository.NewTodo(d.Reader)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	placeID := d.Seeder.Place(t, h.ID)
	open := d.Seeder.Todo(t, h.ID, placeID)
	done := d.Seeder.Todo(t, h.ID, placeID)
	other := d.Seeder.Household(t, hmodel.PlanFree)
	d.Seeder.Todo(t, other.ID, d.Seeder.Place(t, other.ID))

	// act
	d.InTx(t, func(tx tx.Tx) {
		require.NoError(t, todos.Bind(tx).SetCompletion(t.Context(), done, h.ID, &now))
	})
	n, err := todos.CountOpen(t.Context(), h.ID)

	// assert
	require.NoError(t, err)
	assert.Equal(t, 1, n)
	got, err := todos.Find(t.Context(), open, h.ID)
	require.NoError(t, err)
	assert.Nil(t, got.CompletedAt)
}

func TestTodo_SetCompletion_reopenAndMissing(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	todos := repository.NewTodo(d.Reader)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	id := d.Seeder.Todo(t, h.ID, d.Seeder.Place(t, h.ID))

	// act
	d.InTx(t, func(tx tx.Tx) {
		bound := todos.Bind(tx)
		require.NoError(t, bound.SetCompletion(t.Context(), id, h.ID, &now))
		require.NoError(t, bound.SetCompletion(t.Context(), id, h.ID, nil))
		require.NoError(t, bound.SetCompletion(t.Context(), uuid.NewV7(), h.ID, &now), "a missing todo is a no-op")
	})

	// assert
	got, err := todos.Find(t.Context(), id, h.ID)
	require.NoError(t, err)
	assert.Nil(t, got.CompletedAt)
}

func TestTodo_Delete(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	todos := repository.NewTodo(d.Reader)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	id := d.Seeder.Todo(t, h.ID, d.Seeder.Place(t, h.ID))
	other := d.Seeder.Household(t, hmodel.PlanFree)
	elsewhere := d.Seeder.Todo(t, other.ID, d.Seeder.Place(t, other.ID))

	// act
	d.InTx(t, func(tx tx.Tx) {
		bound := todos.Bind(tx)
		require.NoError(t, bound.Delete(t.Context(), id, h.ID))
		require.NoError(t, bound.Delete(t.Context(), id, h.ID), "deleting again is a no-op")
		require.NoError(t, bound.Delete(t.Context(), elsewhere, h.ID), "another household's row is left alone")
	})

	// assert
	_, err := todos.Find(t.Context(), id, h.ID)
	require.ErrorIs(t, err, aerrors.ErrNotFound)
	assert.Equal(t, 1, d.Seeder.Count(t, "todos", other.ID))
}
