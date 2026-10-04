package usecase_test

import (
	"testing"
	"time"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/feature/device/fixture"
	"github.com/mickamy/LocateDo/internal/feature/device/usecase"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/test/tdb"
)

var now = time.Date(2026, 10, 4, 12, 0, 0, 0, time.UTC)

func TestRegisterDevice(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	userID := d.Seeder.User(t)
	dev := fixture.Device()
	ctx := clock.Set(t.Context(), clock.NewFixed(now))

	// act
	err := usecase.NewRegisterDevice(d.Infra()).Do(ctx, usecase.RegisterDeviceInput{UserID: userID, Device: dev})

	// assert
	require.NoError(t, err)
	var owner uuid.UUID
	var lastSeenAt time.Time
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"SELECT user_id, last_seen_at FROM devices WHERE platform = $1 AND push_token = $2",
		string(dev.Platform), dev.PushToken).Scan(&owner, &lastSeenAt))
	assert.Equal(t, userID, owner, "the caller owns the device, whatever the input said")
	assert.True(t, now.Equal(lastSeenAt), "last_seen_at comes from the request clock")
}
