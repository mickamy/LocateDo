package repository_test

import (
	"testing"
	"time"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/device/fixture"
	"github.com/mickamy/LocateDo/internal/feature/device/model"
	"github.com/mickamy/LocateDo/internal/feature/device/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/test/tdb"
)

var now = time.Date(2026, 10, 4, 12, 0, 0, 0, time.UTC)

func TestDevice_Upsert_tokenMovesToTheLatestUser(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	devices := repository.NewDevice(d.Reader)
	first := d.Seeder.User(t)
	second := d.Seeder.User(t)
	dev := fixture.Device(func(m *model.Device) { m.UserID = first; m.LastSeenAt = now })

	// act
	d.InTx(t, func(tx tx.Tx) {
		require.NoError(t, devices.Bind(tx).Upsert(t.Context(), dev))
	})
	dev.UserID = second
	dev.LastSeenAt = now.Add(time.Hour)
	d.InTx(t, func(tx tx.Tx) {
		require.NoError(t, devices.Bind(tx).Upsert(t.Context(), dev))
	})

	// assert
	var count int
	var userID uuid.UUID
	var lastSeenAt time.Time
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"SELECT count(*), max(user_id::text)::uuid, max(last_seen_at) FROM devices WHERE push_token = $1", dev.PushToken).
		Scan(&count, &userID, &lastSeenAt))
	assert.Equal(t, 1, count, "one row per token")
	assert.Equal(t, second, userID)
	assert.True(t, now.Add(time.Hour).Equal(lastSeenAt))
}

func TestDevice_Upsert_unknownUser(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	devices := repository.NewDevice(d.Reader)

	// act
	err := d.Transactor.WithTx(t.Context(), func(tx tx.Tx) error {
		return devices.Bind(tx).Upsert(t.Context(), fixture.Device(func(m *model.Device) { m.LastSeenAt = now }))
	})

	// assert
	require.ErrorIs(t, err, aerrors.ErrInvalidArgument)
}
