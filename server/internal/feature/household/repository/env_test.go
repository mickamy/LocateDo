package repository_test

import (
	"testing"
	"time"
	"uuid"

	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/feature/household/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/test/tdb"
)

var now = time.Date(2026, 10, 3, 12, 0, 0, 0, time.UTC)

func createUser(t *testing.T, d tdb.DB) uuid.UUID {
	t.Helper()

	var id uuid.UUID
	require.NoError(t, d.Writer.QueryRow(t.Context(), "INSERT INTO users DEFAULT VALUES RETURNING id").Scan(&id))
	return id
}

func createHousehold(t *testing.T, d tdb.DB, households repository.Household, ownerID uuid.UUID) uuid.UUID {
	t.Helper()

	id := uuid.NewV7()
	inTx(t, d, func(tx tx.Tx) {
		_, err := households.Bind(tx).Create(t.Context(), id, ownerID)
		require.NoError(t, err)
	})
	return id
}

func inTx(t *testing.T, d tdb.DB, fn func(tx tx.Tx)) {
	t.Helper()

	require.NoError(t, d.Transactor.WithTx(t.Context(), func(tx tx.Tx) error {
		fn(tx)
		return nil
	}))
}
