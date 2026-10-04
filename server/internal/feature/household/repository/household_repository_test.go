package repository_test

import (
	"testing"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/household/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestHousehold_createFindDelete(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	households := repository.NewHousehold(d.Reader)
	ownerID := d.Seeder.User(t)
	id := uuid.NewV7()

	// act
	var created model.Household
	d.InTx(t, func(tx tx.Tx) {
		var err error
		created, err = households.Bind(tx).Create(t.Context(), id, ownerID)
		require.NoError(t, err)
	})
	found, err := households.Find(t.Context(), id)

	// assert
	require.NoError(t, err)
	assert.Equal(t, id, created.ID)
	assert.Equal(t, model.PlanFree, created.Plan)
	assert.Equal(t, created, found)

	d.InTx(t, func(tx tx.Tx) {
		require.NoError(t, households.Bind(tx).Delete(t.Context(), id))
		require.ErrorIs(t, households.Bind(tx).Delete(t.Context(), id), aerrors.ErrNotFound)
	})
	_, err = households.Find(t.Context(), id)
	require.ErrorIs(t, err, aerrors.ErrNotFound)
}

func TestHousehold_Create_duplicateID(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	households := repository.NewHousehold(d.Reader)
	id := createHousehold(t, d, households, d.Seeder.User(t))

	// act
	err := d.Transactor.WithTx(t.Context(), func(tx tx.Tx) error {
		_, err := households.Bind(tx).Create(t.Context(), id, d.Seeder.User(t))
		return err
	})

	// assert
	require.ErrorIs(t, err, aerrors.ErrConflict)
}

func TestHousehold_MoveContents(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	households := repository.NewHousehold(d.Reader)
	from := createHousehold(t, d, households, d.Seeder.User(t))
	to := createHousehold(t, d, households, d.Seeder.User(t))

	fromShopping := d.Seeder.BuiltinCategory(t, from, "shopping")
	fromWork := d.Seeder.BuiltinCategory(t, from, "work")
	custom := d.Seeder.Category(t, from)
	toShopping := d.Seeder.BuiltinCategory(t, to, "shopping")

	inShopping := d.Seeder.CategorizedPlace(t, from, fromShopping)
	inWork := d.Seeder.CategorizedPlace(t, from, fromWork)
	inCustom := d.Seeder.CategorizedPlace(t, from, custom)
	uncategorized := d.Seeder.Place(t, from)
	todo := d.Seeder.Todo(t, from, inShopping)
	versionBefore := d.Seeder.Version(t, to)

	// act
	d.InTx(t, func(tx tx.Tx) {
		require.NoError(t, households.Bind(tx).MoveContents(t.Context(), from, to))
		require.NoError(t, households.Bind(tx).Delete(t.Context(), from))
	})

	// assert
	assert.Equal(t, placeRow{household: to, category: &toShopping}, place(t, d, inShopping))
	assert.Equal(t, placeRow{household: to, category: nil}, place(t, d, inWork), "no work category in destination")
	assert.Equal(t, placeRow{household: to, category: &custom}, place(t, d, inCustom))
	assert.Equal(t, placeRow{household: to, category: nil}, place(t, d, uncategorized))
	assert.Equal(t, to, categoryHousehold(t, d, custom))
	assert.Equal(t, to, todoHousehold(t, d, todo))

	assert.Greater(t, d.Seeder.Version(t, to), versionBefore, "moved rows reach the destination's members")
	_, err := households.Find(t.Context(), from)
	require.ErrorIs(t, err, aerrors.ErrNotFound)
	var leftover int
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"SELECT count(*) FROM categories WHERE id IN ($1, $2)", fromShopping, fromWork).Scan(&leftover))
	assert.Zero(t, leftover, "built-ins of the source household go with it")
}

func TestHousehold_MoveContents_empty(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	households := repository.NewHousehold(d.Reader)
	from := createHousehold(t, d, households, d.Seeder.User(t))
	to := createHousehold(t, d, households, d.Seeder.User(t))

	// act & assert
	d.InTx(t, func(tx tx.Tx) {
		require.NoError(t, households.Bind(tx).MoveContents(t.Context(), from, to))
		require.NoError(t, households.Bind(tx).Delete(t.Context(), from))
	})
}

type placeRow struct {
	household uuid.UUID
	category  *uuid.UUID
}

func place(t *testing.T, d tdb.DB, id uuid.UUID) placeRow {
	t.Helper()

	var row placeRow
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"SELECT household_id, category_id FROM places WHERE id = $1", id).Scan(&row.household, &row.category))
	return row
}

func categoryHousehold(t *testing.T, d tdb.DB, id uuid.UUID) uuid.UUID {
	t.Helper()

	var householdID uuid.UUID
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"SELECT household_id FROM categories WHERE id = $1", id).Scan(&householdID))
	return householdID
}

func todoHousehold(t *testing.T, d tdb.DB, id uuid.UUID) uuid.UUID {
	t.Helper()

	var householdID uuid.UUID
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"SELECT household_id FROM todos WHERE id = $1", id).Scan(&householdID))
	return householdID
}

func TestHousehold_FindForUpdate(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	households := repository.NewHousehold(d.Reader)
	id := createHousehold(t, d, households, d.Seeder.User(t))

	// act
	var locked model.Household
	d.InTx(t, func(tx tx.Tx) {
		var err error
		locked, err = households.Bind(tx).FindForUpdate(t.Context(), id)
		require.NoError(t, err)

		_, err = households.Bind(tx).FindForUpdate(t.Context(), uuid.NewV7())
		require.ErrorIs(t, err, aerrors.ErrNotFound)
	})
	found, err := households.Find(t.Context(), id)

	// assert
	require.NoError(t, err)
	assert.Equal(t, found, locked)
}
