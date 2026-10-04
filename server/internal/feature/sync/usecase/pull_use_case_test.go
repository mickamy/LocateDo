package usecase_test

import (
	"testing"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	hmodel "github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/sync/model"
	"github.com/mickamy/LocateDo/internal/feature/sync/usecase"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestPull_firstSync(t *testing.T) {
	t.Parallel()

	// arrange: one of everything, plus a todo that was deleted before the device ever synced
	d := tdb.New(t)
	h := d.Seeder.Household(t, hmodel.PlanPro)
	memberID := d.Seeder.Member(t, h.ID)
	categoryID := d.Seeder.BuiltinCategory(t, h.ID, "shopping")
	placeID := d.Seeder.CategorizedPlace(t, h.ID, categoryID)
	todoID := d.Seeder.Todo(t, h.ID, placeID)
	gone := d.Seeder.Todo(t, h.ID, placeID)
	_, err := d.Writer.Exec(t.Context(), "DELETE FROM todos WHERE id = $1", gone)
	require.NoError(t, err)

	// act
	out, err := usecase.NewPull(d.Infra()).Do(t.Context(), usecase.PullInput{
		HouseholdID: h.ID, RequestedHouseholdID: h.ID,
	})

	// assert
	require.NoError(t, err)
	assert.False(t, out.HasMore)
	assert.Equal(t, d.Seeder.Version(t, h.ID), out.Cursor, "the final page carries the household version")
	assert.Equal(t, hmodel.PlanPro, out.Household.Plan)
	require.Len(t, out.Changes, 5)
	assert.Equal(t, h.OwnerID, out.Changes[0].Membership.UserID)
	assert.Equal(t, memberID, out.Changes[1].Membership.UserID)
	assert.Equal(t, categoryID, out.Changes[2].Category.ID)
	assert.Equal(t, placeID, out.Changes[3].Place.ID)
	assert.Equal(t, todoID, out.Changes[4].Todo.ID)
	for _, c := range out.Changes {
		assert.Nil(t, c.Deletion, "a first sync carries no tombstones")
	}
}

func TestPull_pages(t *testing.T) {
	t.Parallel()

	// arrange: the owner membership plus four places make five changes
	d := tdb.New(t)
	h := d.Seeder.Household(t, hmodel.PlanPro)
	for range 4 {
		d.Seeder.Place(t, h.ID)
	}
	pull := usecase.NewPull(d.Infra())

	// act
	first, err := pull.Do(t.Context(), usecase.PullInput{HouseholdID: h.ID, RequestedHouseholdID: h.ID, Limit: 2})
	require.NoError(t, err)
	second, err := pull.Do(t.Context(), usecase.PullInput{
		HouseholdID: h.ID, RequestedHouseholdID: h.ID, Cursor: first.Cursor, Limit: 2,
	})
	require.NoError(t, err)
	third, err := pull.Do(t.Context(), usecase.PullInput{
		HouseholdID: h.ID, RequestedHouseholdID: h.ID, Cursor: second.Cursor, Limit: 2,
	})
	require.NoError(t, err)

	// assert
	assert.True(t, first.HasMore)
	assert.Equal(t, first.Changes[1].Version, first.Cursor, "a full page points at its last change")
	assert.True(t, second.HasMore)
	assert.Len(t, second.Changes, 2)
	assert.False(t, third.HasMore)
	assert.Len(t, third.Changes, 1)
	assert.Equal(t, d.Seeder.Version(t, h.ID), third.Cursor)

	var versions []int64
	for _, page := range [][]model.Change{first.Changes, second.Changes, third.Changes} {
		for _, c := range page {
			versions = append(versions, c.Version)
		}
	}
	assert.IsIncreasing(t, versions, "pages join into one ordered stream without gaps or repeats")
}

func TestPull_incrementalCarriesTombstones(t *testing.T) {
	t.Parallel()

	// arrange: a device that synced once, then a todo is deleted and a place renamed
	d := tdb.New(t)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	placeID := d.Seeder.Place(t, h.ID)
	todoID := d.Seeder.Todo(t, h.ID, placeID)
	pull := usecase.NewPull(d.Infra())
	synced, err := pull.Do(t.Context(), usecase.PullInput{HouseholdID: h.ID, RequestedHouseholdID: h.ID})
	require.NoError(t, err)
	_, err = d.Writer.Exec(t.Context(), "DELETE FROM todos WHERE id = $1", todoID)
	require.NoError(t, err)
	_, err = d.Writer.Exec(t.Context(), "UPDATE places SET name = 'Grocery' WHERE id = $1", placeID)
	require.NoError(t, err)

	// act
	out, err := pull.Do(t.Context(), usecase.PullInput{
		HouseholdID: h.ID, RequestedHouseholdID: h.ID, Cursor: synced.Cursor,
	})

	// assert
	require.NoError(t, err)
	require.Len(t, out.Changes, 2)
	assert.Equal(t, &model.Deletion{Kind: model.KindTodo, ID: todoID, Version: out.Changes[0].Version},
		out.Changes[0].Deletion)
	assert.Equal(t, "Grocery", out.Changes[1].Place.Name)
	assert.Greater(t, out.Cursor, synced.Cursor)
}

func TestPull_nothingNew(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	pull := usecase.NewPull(d.Infra())
	synced, err := pull.Do(t.Context(), usecase.PullInput{HouseholdID: h.ID, RequestedHouseholdID: h.ID})
	require.NoError(t, err)

	// act
	out, err := pull.Do(t.Context(), usecase.PullInput{
		HouseholdID: h.ID, RequestedHouseholdID: h.ID, Cursor: synced.Cursor,
	})

	// assert
	require.NoError(t, err)
	assert.Empty(t, out.Changes)
	assert.False(t, out.HasMore)
	assert.Equal(t, synced.Cursor, out.Cursor)
}

func TestPull_anotherHousehold(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	h := d.Seeder.Household(t, hmodel.PlanFree)

	// act
	_, err := usecase.NewPull(d.Infra()).Do(t.Context(), usecase.PullInput{
		HouseholdID: h.ID, RequestedHouseholdID: uuid.NewV7(),
	})

	// assert
	require.ErrorIs(t, err, aerrors.ErrPermissionDenied)
}

func TestPull_resetAfterSweep(t *testing.T) {
	t.Parallel()

	// arrange: a device synced, then a deletion it never saw was swept
	d := tdb.New(t)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	placeID := d.Seeder.Place(t, h.ID)
	pull := usecase.NewPull(d.Infra())
	synced, err := pull.Do(t.Context(), usecase.PullInput{HouseholdID: h.ID, RequestedHouseholdID: h.ID})
	require.NoError(t, err)
	gone := d.Seeder.Todo(t, h.ID, placeID)
	_, err = d.Writer.Exec(t.Context(), "DELETE FROM todos WHERE id = $1", gone)
	require.NoError(t, err)
	_, err = d.Writer.Exec(t.Context(), "DELETE FROM deletions RETURNING version")
	require.NoError(t, err)
	_, err = d.Writer.Exec(t.Context(), "UPDATE households SET swept_version = version WHERE id = $1", h.ID)
	require.NoError(t, err)
	kept := d.Seeder.Todo(t, h.ID, placeID)

	// act
	out, err := pull.Do(t.Context(), usecase.PullInput{
		HouseholdID: h.ID, RequestedHouseholdID: h.ID, Cursor: synced.Cursor, Limit: 1,
	})
	again, err2 := pull.Do(t.Context(), usecase.PullInput{
		HouseholdID: h.ID, RequestedHouseholdID: h.ID, Cursor: out.Cursor,
	})

	// assert
	require.NoError(t, err)
	assert.True(t, out.Reset)
	assert.False(t, out.HasMore, "a reset page is never paged")
	require.Len(t, out.Changes, 3, "membership, place, and the surviving todo, despite limit 1")
	assert.Equal(t, kept, out.Changes[2].Todo.ID)
	assert.Equal(t, d.Seeder.Version(t, h.ID), out.Cursor)
	require.NoError(t, err2)
	assert.False(t, again.Reset, "the new cursor is current")
	assert.Empty(t, again.Changes)
}
