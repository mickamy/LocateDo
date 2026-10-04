package repository_test

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	hmodel "github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/sync/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestTombstones_Sweep(t *testing.T) {
	t.Parallel()

	// arrange: two old tombstones and one fresh one in a household, one old one elsewhere
	d := tdb.New(t)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	placeID := d.Seeder.Place(t, h.ID)
	old1 := d.Seeder.Todo(t, h.ID, placeID)
	old2 := d.Seeder.Todo(t, h.ID, placeID)
	fresh := d.Seeder.Todo(t, h.ID, placeID)
	other := d.Seeder.Household(t, hmodel.PlanFree)
	otherTodo := d.Seeder.Todo(t, other.ID, d.Seeder.Place(t, other.ID))
	for _, id := range []any{old1, old2, fresh, otherTodo} {
		_, err := d.Writer.Exec(t.Context(), "DELETE FROM todos WHERE id = $1", id)
		require.NoError(t, err)
	}
	cutoff := time.Now().Add(-time.Hour)
	_, err := d.Writer.Exec(t.Context(),
		"UPDATE deletions SET deleted_at = $1 WHERE row_id IN ($2, $3, $4)", cutoff.Add(-time.Hour), old1, old2, otherTodo)
	require.NoError(t, err)
	var old2Version int64
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"SELECT version FROM deletions WHERE row_id = $1", old2).Scan(&old2Version))

	// act
	var swept int
	d.InTx(t, func(tx tx.Tx) {
		swept, err = repository.NewTombstones(d.Reader).Bind(tx).Sweep(t.Context(), cutoff)
		require.NoError(t, err)
	})

	// assert
	assert.Equal(t, 3, swept)
	remaining, err := repository.NewChanges(d.Reader).Deletions(t.Context(), h.ID, 0, 10)
	require.NoError(t, err)
	require.Len(t, remaining, 1)
	assert.Equal(t, fresh, remaining[0].ID)
	assert.Equal(t, old2Version, sweptVersion(t, d, h.ID), "the newest swept version per household")
	assert.Positive(t, sweptVersion(t, d, other.ID))
}

func sweptVersion(t *testing.T, d tdb.DB, householdID any) int64 {
	t.Helper()

	var v int64
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"SELECT swept_version FROM households WHERE id = $1", householdID).Scan(&v))
	return v
}
