package repository_test

import (
	"slices"
	"testing"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	hmodel "github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/sync/model"
	"github.com/mickamy/LocateDo/internal/feature/sync/repository"
	tmodel "github.com/mickamy/LocateDo/internal/feature/todo/model"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestChanges_streamsEveryTableInVersionOrder(t *testing.T) {
	t.Parallel()

	// arrange: a household with one of everything, then two deletions
	d := tdb.New(t)
	changes := repository.NewChanges(d.Reader)
	h := d.Seeder.Household(t, hmodel.PlanPro)
	memberID := d.Seeder.Member(t, h.ID)
	categoryID := d.Seeder.BuiltinCategory(t, h.ID, "shopping")
	placeID := d.Seeder.CategorizedPlace(t, h.ID, categoryID)
	todoID := d.Seeder.Todo(t, h.ID, placeID)
	other := d.Seeder.Household(t, hmodel.PlanPro)
	d.Seeder.Place(t, other.ID)
	_, err := d.Writer.Exec(t.Context(), "DELETE FROM todos WHERE id = $1", todoID)
	require.NoError(t, err)
	_, err = d.Writer.Exec(t.Context(), "DELETE FROM categories WHERE id = $1", categoryID)
	require.NoError(t, err)

	// act
	memberships, err := changes.Memberships(t.Context(), h.ID, 0, 10)
	require.NoError(t, err)
	categories, err := changes.Categories(t.Context(), h.ID, 0, 10)
	require.NoError(t, err)
	places, err := changes.Places(t.Context(), h.ID, 0, 10)
	require.NoError(t, err)
	todos, err := changes.Todos(t.Context(), h.ID, 0, 10)
	require.NoError(t, err)
	deletions, err := changes.Deletions(t.Context(), h.ID, 0, 10)
	require.NoError(t, err)

	// assert
	require.Len(t, memberships, 2)
	assert.Equal(t, h.OwnerID, memberships[0].UserID)
	assert.Equal(t, memberID, memberships[1].UserID)
	assert.Empty(t, categories, "the deleted category is gone")
	require.Len(t, places, 1)
	assert.Nil(t, places[0].CategoryID, "the place became uncategorized when its category went")
	assert.Empty(t, todos)
	require.Len(t, deletions, 2)
	assert.Equal(t, model.Deletion{Kind: model.KindTodo, ID: todoID, Version: deletions[0].Version}, deletions[0])
	assert.Equal(t, model.Deletion{Kind: model.KindCategory, ID: categoryID, Version: deletions[1].Version}, deletions[1])

	assert.Less(t, memberships[0].Version, memberships[1].Version, "versions grow in write order")
	versions := []int64{
		memberships[0].Version, memberships[1].Version, places[0].Version, deletions[0].Version, deletions[1].Version,
	}
	slices.Sort(versions)
	assert.Equal(t, versions, slices.Compact(slices.Clone(versions)), "no two rows share a version")
}

func TestChanges_todoCreatorAndCompleter(t *testing.T) {
	t.Parallel()

	// arrange: the owner adds a to-do, the member completes it, the owner reopens and completes it
	d := tdb.New(t)
	changes := repository.NewChanges(d.Reader)
	h := d.Seeder.Household(t, hmodel.PlanPro)
	memberID := d.Seeder.Member(t, h.ID)
	reopened := d.Seeder.Todo(t, h.ID, d.Seeder.Place(t, h.ID))
	open := d.Seeder.Todo(t, h.ID, d.Seeder.Place(t, h.ID))
	_, err := d.Writer.Exec(t.Context(), "UPDATE todos SET creator_id = $1, completed_at = now() WHERE id = $2",
		h.OwnerID, reopened)
	require.NoError(t, err)
	_, err = d.Writer.Exec(t.Context(),
		`INSERT INTO todo_completions (todo_id, completer_id, completed_at, reopened_at)
		 VALUES ($1, $2, now(), now()), ($1, $3, now(), NULL), ($4, $2, now(), now())`,
		reopened, memberID, h.OwnerID, open)
	require.NoError(t, err)

	// act
	todos, err := changes.Todos(t.Context(), h.ID, 0, 10)

	// assert
	require.NoError(t, err)
	byID := map[uuid.UUID]tmodel.Todo{}
	for _, td := range todos {
		byID[td.ID] = td
	}
	assert.Equal(t, &h.OwnerID, byID[reopened].CreatorID)
	assert.Equal(t, &h.OwnerID, byID[reopened].CompleterID, "the latest completion not reopened")
	assert.Nil(t, byID[open].CreatorID, "a to-do from before creators were recorded")
	assert.Nil(t, byID[open].CompleterID, "its only completion was reopened")
}

func TestChanges_cursorAndLimit(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	changes := repository.NewChanges(d.Reader)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	ids := make([]uuid.UUID, 3)
	for i := range ids {
		ids[i] = d.Seeder.Place(t, h.ID)
	}
	all, err := changes.Places(t.Context(), h.ID, 0, 10)
	require.NoError(t, err)
	require.Len(t, all, 3)

	// act
	limited, err := changes.Places(t.Context(), h.ID, 0, 2)
	require.NoError(t, err)
	after, err := changes.Places(t.Context(), h.ID, all[0].Version, 10)
	require.NoError(t, err)
	none, err := changes.Places(t.Context(), h.ID, all[2].Version, 10)
	require.NoError(t, err)

	// assert
	assert.Equal(t, []uuid.UUID{limited[0].ID, limited[1].ID}, ids[:2])
	assert.Equal(t, []uuid.UUID{after[0].ID, after[1].ID}, ids[1:])
	assert.Empty(t, none)
}
