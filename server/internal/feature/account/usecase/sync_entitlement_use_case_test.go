package usecase_test

import (
	"testing"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/feature/account/usecase"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestSyncEntitlement_enqueuesACheck(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	userID := d.Seeder.User(t)

	// act
	err := usecase.NewSyncEntitlement(d.Infra()).Do(t.Context(), usecase.SyncEntitlementInput{UserID: userID})

	// assert
	require.NoError(t, err)
	assert.Equal(t, 1, pendingEntitlementChecks(t, d, userID))
}

func TestSyncEntitlement_repeatedCallsShareOneCheck(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	userID := d.Seeder.User(t)
	syncEntitlement := usecase.NewSyncEntitlement(d.Infra())

	// act
	for range 3 {
		require.NoError(t, syncEntitlement.Do(t.Context(), usecase.SyncEntitlementInput{UserID: userID}))
	}

	// assert
	assert.Equal(t, 1, pendingEntitlementChecks(t, d, userID))
}

func pendingEntitlementChecks(t *testing.T, d tdb.DB, userID uuid.UUID) int {
	t.Helper()

	var n int
	require.NoError(t, d.Writer.QueryRow(t.Context(), `
		SELECT count(*) FROM outbox_messages
		WHERE kind = 'sync_entitlement' AND status = 'pending' AND payload->>'user_id' = $1`,
		userID.String()).Scan(&n))
	return n
}
