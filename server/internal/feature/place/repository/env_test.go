package repository_test

import (
	"testing"
	"uuid"

	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/test/tdb"
)

func createHousehold(t *testing.T, d tdb.DB) uuid.UUID {
	t.Helper()

	var id uuid.UUID
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		`WITH u AS (INSERT INTO users DEFAULT VALUES RETURNING id)
		 INSERT INTO households (owner_id) SELECT id FROM u RETURNING id`).Scan(&id))
	return id
}

func createCategory(t *testing.T, d tdb.DB, householdID uuid.UUID) uuid.UUID {
	t.Helper()

	var id uuid.UUID
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		`INSERT INTO categories (household_id, name, icon, color)
		 VALUES ($1, 'Drugstore', 'cart', 'green') RETURNING id`, householdID).Scan(&id))
	return id
}

func createPlace(t *testing.T, d tdb.DB, householdID uuid.UUID) uuid.UUID {
	t.Helper()

	var id uuid.UUID
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		`INSERT INTO places (household_id, name, lat, lng)
		 VALUES ($1, 'store', 35.0, 139.0) RETURNING id`, householdID).Scan(&id))
	return id
}

func inTx(t *testing.T, d tdb.DB, fn func(tx tx.Tx)) {
	t.Helper()

	require.NoError(t, d.Transactor.WithTx(t.Context(), func(tx tx.Tx) error {
		fn(tx)
		return nil
	}))
}
