package job_test

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	hmodel "github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/infra/revenuecat"
	"github.com/mickamy/LocateDo/internal/outbox"
	"github.com/mickamy/LocateDo/internal/worker/job"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestSyncEntitlement(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name   string
		plan   hmodel.Plan
		active bool
		err    error
		want   hmodel.Plan
		pushed bool
	}{
		{
			name: "purchase upgrades and wakes the members",
			plan: hmodel.PlanFree, active: true, want: hmodel.PlanPro, pushed: true,
		},
		{
			name: "expiry downgrades and wakes the members",
			plan: hmodel.PlanPro, active: false, want: hmodel.PlanFree, pushed: true,
		},
		{
			name: "no change stays quiet",
			plan: hmodel.PlanPro, active: true, want: hmodel.PlanPro,
		},
		{
			name: "unconfigured leaves the plan",
			plan: hmodel.PlanFree, err: revenuecat.ErrNotConfigured, want: hmodel.PlanFree,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			// arrange
			d := tdb.New(t)
			h := d.Seeder.Household(t, tt.plan)
			clearPushes(t, d)

			// act
			err := entitlementJob(d, fakeEntitlements{active: tt.active, err: tt.err}).
				Handle(t.Context(), outbox.SyncEntitlement(h.OwnerID, time.Now()))

			// assert
			require.NoError(t, err)
			assert.Equal(t, string(tt.want), planOf(t, d, h.ID))
			assert.Equal(t, tt.pushed, pushes(t, d) == 1)
		})
	}
}

func TestSyncEntitlement_userWithoutAHousehold(t *testing.T) {
	t.Parallel()

	d := tdb.New(t)

	err := entitlementJob(d, fakeEntitlements{active: true}).
		Handle(t.Context(), outbox.SyncEntitlement(d.Seeder.User(t), time.Now()))

	require.NoError(t, err)
}

func TestSyncEntitlement_retriesWhenRevenueCatFails(t *testing.T) {
	t.Parallel()

	d := tdb.New(t)
	h := d.Seeder.Household(t, hmodel.PlanPro)

	err := entitlementJob(d, fakeEntitlements{err: errors.New("status 503")}).
		Handle(t.Context(), outbox.SyncEntitlement(h.OwnerID, time.Now()))

	require.ErrorContains(t, err, "503")
	assert.Equal(t, string(hmodel.PlanPro), planOf(t, d, h.ID))
}

type fakeEntitlements struct {
	active bool
	err    error
}

func (f fakeEntitlements) Active(context.Context, string, time.Time) (bool, error) {
	return f.active, f.err
}

func entitlementJob(d tdb.DB, e revenuecat.Entitlements) *job.SyncEntitlement {
	infra := d.Infra()
	infra.Entitlements = e
	return job.NewSyncEntitlement(infra)
}

func planOf(t *testing.T, d tdb.DB, householdID any) string {
	t.Helper()

	var plan string
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"SELECT plan FROM households WHERE id = $1", householdID).Scan(&plan))
	return plan
}

func clearPushes(t *testing.T, d tdb.DB) {
	t.Helper()

	_, err := d.Writer.Exec(t.Context(), "DELETE FROM outbox_messages WHERE kind = 'push_household'")
	require.NoError(t, err)
}

func pushes(t *testing.T, d tdb.DB) int {
	t.Helper()

	return rows(t, d, "SELECT count(*) FROM outbox_messages WHERE kind = 'push_household'")
}
