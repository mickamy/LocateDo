package migrate_test

import (
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgconn"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/infra/storage/db"
	"github.com/mickamy/LocateDo/test/tdb"
)

const foreignKeyViolationCode = "23503"

func TestSyncTriggers_stampVersionAndUpdatedAt(t *testing.T) {
	t.Parallel()

	// arrange
	w := tdb.New(t).Writer
	householdID := createHousehold(t, w)
	placeID := createPlace(t, w, householdID)

	// act
	var todoID string
	require.NoError(t, w.QueryRow(t.Context(),
		"INSERT INTO todos (household_id, place_id, title) VALUES ($1, $2, 'milk') RETURNING id",
		householdID, placeID).Scan(&todoID))
	_, err := w.Exec(t.Context(), "UPDATE todos SET title = 'two milks' WHERE id = $1", todoID)
	require.NoError(t, err)

	// assert
	assert.Equal(t, int64(1), versionOf(t, w, "places", placeID))
	assert.Equal(t, int64(3), versionOf(t, w, "todos", todoID))
	assert.Equal(t, int64(3), householdVersion(t, w, householdID))

	var updatedAt time.Time
	require.NoError(t, w.QueryRow(t.Context(),
		"SELECT updated_at FROM todos WHERE id = $1", todoID).Scan(&updatedAt))
	assert.WithinDuration(t, time.Now(), updatedAt, time.Minute)
}

func TestSyncTriggers_ignoreClientUpdatedAt(t *testing.T) {
	t.Parallel()

	// arrange
	w := tdb.New(t).Writer
	householdID := createHousehold(t, w)
	future := time.Now().AddDate(1, 0, 0)

	// act
	var updatedAt time.Time
	require.NoError(t, w.QueryRow(t.Context(),
		`INSERT INTO places (household_id, name, lat, lng, updated_at, version)
		 VALUES ($1, 'store', 35.0, 139.0, $2, 999) RETURNING updated_at`,
		householdID, future).Scan(&updatedAt))

	// assert
	assert.WithinDuration(t, time.Now(), updatedAt, time.Minute)
	assert.Equal(t, int64(1), householdVersion(t, w, householdID))
}

func TestSyncTriggers_unknownHousehold(t *testing.T) {
	t.Parallel()

	// arrange
	w := tdb.New(t).Writer

	// act
	_, err := w.Exec(t.Context(),
		"INSERT INTO places (household_id, name, lat, lng) VALUES (uuidv7(), 'store', 35.0, 139.0)")

	// assert
	assertPgCode(t, err, foreignKeyViolationCode)
}

func TestTodos_placeInAnotherHousehold(t *testing.T) {
	t.Parallel()

	// arrange
	w := tdb.New(t).Writer
	mine := createHousehold(t, w)
	theirs := createHousehold(t, w)
	theirPlace := createPlace(t, w, theirs)

	// act
	_, err := w.Exec(t.Context(),
		"INSERT INTO todos (household_id, place_id, title) VALUES ($1, $2, 'milk')", mine, theirPlace)

	// assert
	assertPgCode(t, err, foreignKeyViolationCode)
}

func TestDeletions_placeCascadesToTodos(t *testing.T) {
	t.Parallel()

	// arrange
	w := tdb.New(t).Writer
	householdID := createHousehold(t, w)
	placeID := createPlace(t, w, householdID)
	var todoID string
	require.NoError(t, w.QueryRow(t.Context(),
		"INSERT INTO todos (household_id, place_id, title) VALUES ($1, $2, 'milk') RETURNING id",
		householdID, placeID).Scan(&todoID))
	before := householdVersion(t, w, householdID)

	// act
	_, err := w.Exec(t.Context(), "DELETE FROM places WHERE id = $1", placeID)
	require.NoError(t, err)

	// assert
	got := tombstones(t, w, householdID)
	assert.ElementsMatch(t, []tombstone{
		{Table: "places", RowID: placeID},
		{Table: "todos", RowID: todoID},
	}, withoutVersions(got))
	for _, ts := range got {
		assert.Greater(t, ts.Version, before)
	}
	assert.Equal(t, before+2, householdVersion(t, w, householdID))
}

func TestDeletions_categoryUncategorizesPlaces(t *testing.T) {
	t.Parallel()

	// arrange
	w := tdb.New(t).Writer
	householdID := createHousehold(t, w)
	var categoryID string
	require.NoError(t, w.QueryRow(t.Context(),
		`INSERT INTO categories (household_id, builtin_key, icon, color)
		 VALUES ($1, 'shopping', 'cart', 'green') RETURNING id`, householdID).Scan(&categoryID))
	placeID := createPlace(t, w, householdID)
	_, err := w.Exec(t.Context(), "UPDATE places SET category_id = $1 WHERE id = $2", categoryID, placeID)
	require.NoError(t, err)
	before := versionOf(t, w, "places", placeID)

	// act
	_, err = w.Exec(t.Context(), "DELETE FROM categories WHERE id = $1", categoryID)
	require.NoError(t, err)

	// assert
	var got *string
	require.NoError(t, w.QueryRow(t.Context(),
		"SELECT category_id FROM places WHERE id = $1", placeID).Scan(&got))
	assert.Nil(t, got)
	assert.Greater(t, versionOf(t, w, "places", placeID), before)
	assert.Equal(t, []tombstone{{Table: "categories", RowID: categoryID}}, withoutVersions(tombstones(t, w, householdID)))
}

func TestDeletions_membershipUsesUserID(t *testing.T) {
	t.Parallel()

	// arrange
	w := tdb.New(t).Writer
	householdID := createHousehold(t, w)
	var userID string
	require.NoError(t, w.QueryRow(t.Context(),
		"INSERT INTO users DEFAULT VALUES RETURNING id").Scan(&userID))
	_, err := w.Exec(t.Context(),
		"INSERT INTO memberships (household_id, user_id, role) VALUES ($1, $2, 'member')", householdID, userID)
	require.NoError(t, err)

	// act
	_, err = w.Exec(t.Context(),
		"DELETE FROM memberships WHERE household_id = $1 AND user_id = $2", householdID, userID)
	require.NoError(t, err)

	// assert
	assert.Equal(t, []tombstone{{Table: "memberships", RowID: userID}}, withoutVersions(tombstones(t, w, householdID)))
}

func TestDeletions_householdLeavesNoTombstones(t *testing.T) {
	t.Parallel()

	// arrange
	w := tdb.New(t).Writer
	householdID := createHousehold(t, w)
	createPlace(t, w, householdID)

	// act
	_, err := w.Exec(t.Context(), "DELETE FROM households WHERE id = $1", householdID)

	// assert
	require.NoError(t, err)
	assert.Empty(t, tombstones(t, w, householdID))
}

type tombstone struct {
	Table   string
	RowID   string
	Version int64
}

func tombstones(t *testing.T, w db.Writer, householdID string) []tombstone {
	t.Helper()

	rows, err := w.Query(t.Context(),
		"SELECT table_name, row_id, version FROM deletions WHERE household_id = $1 ORDER BY version", householdID)
	require.NoError(t, err)
	defer rows.Close()

	var got []tombstone
	for rows.Next() {
		var ts tombstone
		require.NoError(t, rows.Scan(&ts.Table, &ts.RowID, &ts.Version))
		got = append(got, ts)
	}
	require.NoError(t, rows.Err())
	return got
}

func withoutVersions(ts []tombstone) []tombstone {
	out := make([]tombstone, len(ts))
	for i, t := range ts {
		out[i] = tombstone{Table: t.Table, RowID: t.RowID}
	}
	return out
}

func createHousehold(t *testing.T, w db.Writer) string {
	t.Helper()

	var userID, householdID string
	require.NoError(t, w.QueryRow(t.Context(),
		"INSERT INTO users DEFAULT VALUES RETURNING id").Scan(&userID))
	require.NoError(t, w.QueryRow(t.Context(),
		"INSERT INTO households (owner_id) VALUES ($1) RETURNING id", userID).Scan(&householdID))
	return householdID
}

func createPlace(t *testing.T, w db.Writer, householdID string) string {
	t.Helper()

	var placeID string
	require.NoError(t, w.QueryRow(t.Context(),
		"INSERT INTO places (household_id, name, lat, lng) VALUES ($1, 'store', 35.0, 139.0) RETURNING id",
		householdID).Scan(&placeID))
	return placeID
}

func versionOf(t *testing.T, w db.Writer, table, id string) int64 {
	t.Helper()

	var v int64
	require.NoError(t, w.QueryRow(t.Context(),
		"SELECT version FROM "+table+" WHERE id = $1", id).Scan(&v))
	return v
}

func householdVersion(t *testing.T, w db.Writer, id string) int64 {
	t.Helper()

	return versionOf(t, w, "households", id)
}

func assertPgCode(t *testing.T, err error, code string) {
	t.Helper()

	var pgErr *pgconn.PgError
	require.ErrorAs(t, err, &pgErr)
	assert.Equal(t, code, pgErr.Code)
}
