package job_test

import (
	"testing"
	"time"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	hmodel "github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/internal/outbox"
	"github.com/mickamy/LocateDo/internal/worker/job"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestReconcileEntitlements_Run(t *testing.T) {
	t.Parallel()

	// arrange: two Pro households, one with a check already queued, and a free one
	d := tdb.New(t)
	now := time.Now()
	queued := d.Seeder.Household(t, hmodel.PlanPro)
	pro := d.Seeder.Household(t, hmodel.PlanPro)
	d.Seeder.Household(t, hmodel.PlanFree)
	d.InTx(t, func(tx tx.Tx) {
		messages := outbox.NewRepository(d.Reader).Bind(tx)
		require.NoError(t, messages.Enqueue(t.Context(), outbox.SyncEntitlement(queued.OwnerID, now)))
	})

	// act
	err := job.NewReconcileEntitlements(d.Infra()).Run(clock.Set(t.Context(), clock.NewFixed(now)))

	// assert
	require.NoError(t, err)
	var users []uuid.UUID
	rows, err := d.Writer.Query(t.Context(),
		"SELECT (payload->>'user_id')::uuid FROM outbox_messages WHERE kind = 'sync_entitlement' AND status = 'pending'")
	require.NoError(t, err)
	for rows.Next() {
		var id uuid.UUID
		require.NoError(t, rows.Scan(&id))
		users = append(users, id)
	}
	require.NoError(t, rows.Err())
	assert.ElementsMatch(t, []uuid.UUID{queued.OwnerID, pro.OwnerID}, users)
}
