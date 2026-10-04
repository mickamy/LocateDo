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
	ownerID := createUser(t, d)
	id := uuid.NewV7()

	// act
	var created model.Household
	inTx(t, d, func(tx tx.Tx) {
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

	inTx(t, d, func(tx tx.Tx) {
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
	id := createHousehold(t, d, households, createUser(t, d))

	// act
	err := d.Transactor.WithTx(t.Context(), func(tx tx.Tx) error {
		_, err := households.Bind(tx).Create(t.Context(), id, createUser(t, d))
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
	from := createHousehold(t, d, households, createUser(t, d))
	to := createHousehold(t, d, households, createUser(t, d))

	fromShopping := createCategory(t, d, from, "shopping")
	fromWork := createCategory(t, d, from, "work")
	custom := createCategory(t, d, from, "")
	toShopping := createCategory(t, d, to, "shopping")

	inShopping := createPlace(t, d, from, &fromShopping)
	inWork := createPlace(t, d, from, &fromWork)
	inCustom := createPlace(t, d, from, &custom)
	uncategorized := createPlace(t, d, from, nil)
	todo := createTodo(t, d, from, inShopping)
	versionBefore := householdVersion(t, d, to)

	// act
	inTx(t, d, func(tx tx.Tx) {
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

	assert.Greater(t, householdVersion(t, d, to), versionBefore, "moved rows reach the destination's members")
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
	from := createHousehold(t, d, households, createUser(t, d))
	to := createHousehold(t, d, households, createUser(t, d))

	// act & assert
	inTx(t, d, func(tx tx.Tx) {
		require.NoError(t, households.Bind(tx).MoveContents(t.Context(), from, to))
		require.NoError(t, households.Bind(tx).Delete(t.Context(), from))
	})
}

type placeRow struct {
	household uuid.UUID
	category  *uuid.UUID
}

func createCategory(t *testing.T, d tdb.DB, householdID uuid.UUID, builtinKey string) uuid.UUID {
	t.Helper()

	var key, name *string
	if builtinKey == "" {
		custom := "Drugstore"
		name = &custom
	} else {
		key = &builtinKey
	}
	var id uuid.UUID
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		`INSERT INTO categories (household_id, builtin_key, name, icon, color)
		 VALUES ($1, $2, $3, 'cart', 'green') RETURNING id`, householdID, key, name).Scan(&id))
	return id
}

func createPlace(t *testing.T, d tdb.DB, householdID uuid.UUID, categoryID *uuid.UUID) uuid.UUID {
	t.Helper()

	var id uuid.UUID
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		`INSERT INTO places (household_id, name, lat, lng, category_id)
		 VALUES ($1, 'store', 35.0, 139.0, $2) RETURNING id`, householdID, categoryID).Scan(&id))
	return id
}

func createTodo(t *testing.T, d tdb.DB, householdID, placeID uuid.UUID) uuid.UUID {
	t.Helper()

	var id uuid.UUID
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"INSERT INTO todos (household_id, place_id, title) VALUES ($1, $2, 'milk') RETURNING id",
		householdID, placeID).Scan(&id))
	return id
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

func householdVersion(t *testing.T, d tdb.DB, id uuid.UUID) int64 {
	t.Helper()

	var v int64
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"SELECT version FROM households WHERE id = $1", id).Scan(&v))
	return v
}

func TestHousehold_FindForUpdate(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	households := repository.NewHousehold(d.Reader)
	id := createHousehold(t, d, households, createUser(t, d))

	// act
	var locked model.Household
	inTx(t, d, func(tx tx.Tx) {
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
