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
	first := d.Seeder.User(t)
	second := d.Seeder.User(t)
	dev := fixture.Device(func(m *model.Device) { m.UserID = new(first); m.LastSeenAt = now })

	// act
	upsert(t, d, dev)
	dev.UserID = new(second)
	dev.LastSeenAt = now.Add(time.Hour)
	upsert(t, d, dev)

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

func TestDevice_Upsert_withoutUserKeepsTheOwner(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	owner := d.Seeder.User(t)
	dev := fixture.Device(func(m *model.Device) { m.UserID = new(owner); m.LastSeenAt = now })
	upsert(t, d, dev)

	// act
	dev.UserID = nil
	dev.Language = model.LanguageJapanese
	upsert(t, d, dev)

	// assert
	var userID *uuid.UUID
	var language string
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"SELECT user_id, language FROM devices WHERE push_token = $1", dev.PushToken).Scan(&userID, &language))
	assert.Equal(t, new(owner), userID)
	assert.Equal(t, "ja", language)
}

func TestDevice_Upsert_promotionsConsentedAt(t *testing.T) {
	t.Parallel()

	later := now.Add(time.Hour)
	tests := map[string]struct {
		before *bool
		after  bool
		want   *time.Time
	}{
		"new row without consent": {before: nil, after: false, want: nil},
		"new row with consent":    {before: nil, after: true, want: &later},
		"consent given":           {before: new(false), after: true, want: &later},
		"consent withdrawn":       {before: new(true), after: false, want: nil},
		"consent kept":            {before: new(true), after: true, want: &now},
	}
	for name, tt := range tests {
		t.Run(name, func(t *testing.T) {
			t.Parallel()

			// arrange
			d := tdb.New(t)
			dev := fixture.Device(func(m *model.Device) { m.LastSeenAt = now })
			if tt.before != nil {
				dev.PromotionsConsent = *tt.before
				upsert(t, d, dev)
			}

			// act
			dev.PromotionsConsent = tt.after
			dev.LastSeenAt = later
			upsert(t, d, dev)

			// assert
			var got *time.Time
			require.NoError(t, d.Writer.QueryRow(t.Context(),
				"SELECT promotions_consented_at FROM devices WHERE push_token = $1", dev.PushToken).Scan(&got))
			if tt.want == nil {
				assert.Nil(t, got)
				return
			}
			require.NotNil(t, got)
			assert.True(t, tt.want.Equal(*got), "got %v", *got)
		})
	}
}

func TestDevice_Upsert_completionNotices(t *testing.T) {
	t.Parallel()

	tests := map[string]struct {
		before *bool
		sent   *bool
		want   bool
	}{
		"new device, unsaid":           {before: nil, sent: nil, want: true},
		"new device, off":              {before: nil, sent: new(false), want: false},
		"turned off stays when unsaid": {before: new(false), sent: nil, want: false},
		"turned on again":              {before: new(false), sent: new(true), want: true},
	}
	for name, tt := range tests {
		t.Run(name, func(t *testing.T) {
			t.Parallel()

			// arrange
			d := tdb.New(t)
			dev := fixture.Device(func(m *model.Device) { m.LastSeenAt = now })
			if tt.before != nil {
				dev.CompletionNotices = tt.before
				upsert(t, d, dev)
			}

			// act
			dev.CompletionNotices = tt.sent
			upsert(t, d, dev)

			// assert
			var got bool
			require.NoError(t, d.Writer.QueryRow(t.Context(),
				"SELECT completion_notices FROM devices WHERE push_token = $1", dev.PushToken).Scan(&got))
			assert.Equal(t, tt.want, got)
		})
	}
}

func TestDevice_Upsert_apnsEnvironment(t *testing.T) {
	t.Parallel()

	tests := map[string]struct {
		platform model.Platform
		env      model.APNsEnvironment
		want     *string
	}{
		"iOS keeps its environment": {platform: model.PlatformIOS, env: model.APNsSandbox, want: new("sandbox")},
		"Android stores NULL":       {platform: model.PlatformAndroid, env: "", want: nil},
	}
	for name, tt := range tests {
		t.Run(name, func(t *testing.T) {
			t.Parallel()

			// arrange
			d := tdb.New(t)
			dev := fixture.Device(func(m *model.Device) {
				m.UserID = new(d.Seeder.User(t))
				m.Platform = tt.platform
				m.APNsEnvironment = tt.env
				m.LastSeenAt = now
			})

			// act
			upsert(t, d, dev)

			// assert
			var got *string
			require.NoError(t, d.Writer.QueryRow(t.Context(),
				"SELECT apns_environment FROM devices WHERE push_token = $1", dev.PushToken).Scan(&got))
			assert.Equal(t, tt.want, got)
		})
	}
}

func TestDevice_Upsert_unknownUser(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	devices := repository.NewDevice(d.Reader)
	dev := fixture.Device(func(m *model.Device) { m.UserID = new(uuid.NewV7()); m.LastSeenAt = now })

	// act
	err := d.Transactor.WithTx(t.Context(), func(tx tx.Tx) error {
		_, err := devices.Bind(tx).Upsert(t.Context(), dev)
		return err
	})

	// assert
	require.ErrorIs(t, err, aerrors.ErrInvalidArgument)
}

func TestDevice_FindByTokenForUpdate(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	devices := repository.NewDevice(d.Reader)
	dev := fixture.Device(func(m *model.Device) { m.LastSeenAt = now })
	dev.ID = upsert(t, d, dev)

	// act
	var got model.Device
	d.InTx(t, func(tx tx.Tx) {
		var err error
		got, err = devices.Bind(tx).FindByTokenForUpdate(t.Context(), dev.Platform, dev.PushToken)
		require.NoError(t, err)
	})

	// assert
	assert.Equal(t, dev.ID, got.ID)
	assert.Nil(t, got.UserID)
	assert.Equal(t, dev.Language, got.Language)
	assert.Equal(t, dev.PromotionsConsent, got.PromotionsConsent)
}

func TestDevice_FindByTokenForUpdate_notFound(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	devices := repository.NewDevice(d.Reader)

	// act
	err := d.Transactor.WithTx(t.Context(), func(tx tx.Tx) error {
		_, err := devices.Bind(tx).FindByTokenForUpdate(t.Context(), model.PlatformIOS, "unknown")
		return err
	})

	// assert
	require.ErrorIs(t, err, aerrors.ErrNotFound)
}

func TestDevice_Delete(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	devices := repository.NewDevice(d.Reader)
	dev := fixture.Device(func(m *model.Device) { m.LastSeenAt = now })
	id := upsert(t, d, dev)

	// act
	d.InTx(t, func(tx tx.Tx) {
		require.NoError(t, devices.Bind(tx).Delete(t.Context(), id))
	})

	// assert
	assert.Equal(t, 0, countDevices(t, d, dev.PushToken))
}

func TestDevice_ReleaseOwnedByToken(t *testing.T) {
	t.Parallel()

	tests := map[string]struct {
		consent bool
		wantRow bool
	}{
		"consenting device is detached": {consent: true, wantRow: true},
		"other devices are deleted":     {consent: false, wantRow: false},
	}
	for name, tt := range tests {
		t.Run(name, func(t *testing.T) {
			t.Parallel()

			// arrange
			d := tdb.New(t)
			devices := repository.NewDevice(d.Reader)
			owner := d.Seeder.User(t)
			dev := fixture.Device(func(m *model.Device) {
				m.UserID = new(owner)
				m.PromotionsConsent = tt.consent
				m.LastSeenAt = now
			})
			upsert(t, d, dev)

			// act
			d.InTx(t, func(tx tx.Tx) {
				require.NoError(t, devices.Bind(tx).ReleaseOwnedByToken(t.Context(), owner, dev.Platform, dev.PushToken))
			})

			// assert
			if !tt.wantRow {
				assert.Equal(t, 0, countDevices(t, d, dev.PushToken))
				return
			}
			var userID *uuid.UUID
			require.NoError(t, d.Writer.QueryRow(t.Context(),
				"SELECT user_id FROM devices WHERE push_token = $1", dev.PushToken).Scan(&userID))
			assert.Nil(t, userID)
		})
	}
}

func TestDevice_ReleaseOwnedByToken_anotherUsersToken(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	devices := repository.NewDevice(d.Reader)
	owner := d.Seeder.User(t)
	other := d.Seeder.User(t)
	dev := fixture.Device(func(m *model.Device) { m.UserID = new(owner); m.LastSeenAt = now })
	upsert(t, d, dev)

	// act
	d.InTx(t, func(tx tx.Tx) {
		require.NoError(t, devices.Bind(tx).ReleaseOwnedByToken(t.Context(), other, dev.Platform, dev.PushToken))
	})

	// assert
	var userID *uuid.UUID
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"SELECT user_id FROM devices WHERE push_token = $1", dev.PushToken).Scan(&userID))
	assert.Equal(t, new(owner), userID)
}

func TestDevice_DeleteAnonymousUnseenSince(t *testing.T) {
	t.Parallel()

	// arrange: an anonymous device unseen since before the cutoff, one seen after, and an old owned one
	d := tdb.New(t)
	devices := repository.NewDevice(d.Reader)
	cutoff := now.Add(-model.AnonymousRetention)
	stale := fixture.Device(func(m *model.Device) { m.LastSeenAt = cutoff.Add(-time.Hour) })
	fresh := fixture.Device(func(m *model.Device) { m.LastSeenAt = cutoff.Add(time.Hour) })
	owned := fixture.Device(func(m *model.Device) {
		m.UserID = new(d.Seeder.User(t))
		m.LastSeenAt = cutoff.Add(-time.Hour)
	})
	for _, dev := range []model.Device{stale, fresh, owned} {
		upsert(t, d, dev)
	}

	// act
	var swept int
	d.InTx(t, func(tx tx.Tx) {
		var err error
		swept, err = devices.Bind(tx).DeleteAnonymousUnseenSince(t.Context(), cutoff)
		require.NoError(t, err)
	})

	// assert
	assert.Equal(t, 1, swept)
	assert.Equal(t, 0, countDevices(t, d, stale.PushToken))
	assert.Equal(t, 1, countDevices(t, d, fresh.PushToken))
	assert.Equal(t, 1, countDevices(t, d, owned.PushToken))
}

func upsert(t *testing.T, d tdb.DB, dev model.Device) uuid.UUID {
	t.Helper()

	var id uuid.UUID
	d.InTx(t, func(tx tx.Tx) {
		var err error
		id, err = repository.NewDevice(d.Reader).Bind(tx).Upsert(t.Context(), dev)
		require.NoError(t, err)
	})
	return id
}

func countDevices(t *testing.T, d tdb.DB, pushToken string) int {
	t.Helper()

	var n int
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"SELECT count(*) FROM devices WHERE push_token = $1", pushToken).Scan(&n))
	return n
}
