package usecase_test

import (
	"errors"
	"testing"
	"time"
	"uuid"

	"github.com/jackc/pgx/v5"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/feature/device/fixture"
	"github.com/mickamy/LocateDo/internal/feature/device/model"
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
	err := usecase.NewRegisterDevice(d.Infra()).Do(ctx, usecase.RegisterDeviceInput{UserID: &userID, Device: dev})

	// assert
	require.NoError(t, err)
	row, ok := findDevice(t, d, dev)
	require.True(t, ok)
	assert.Equal(t, &userID, row.userID, "the caller owns the device, whatever the input said")
	assert.True(t, now.Equal(row.lastSeenAt), "last_seen_at comes from the request clock")
}

func TestRegisterDevice_consent(t *testing.T) {
	t.Parallel()

	type existing int
	const (
		none existing = iota
		anonymous
		owned
	)
	tests := []struct {
		name        string
		existing    existing
		signedIn    bool
		consent     bool
		wantRow     bool
		wantOwned   bool
		wantConsent bool
		wantChange  bool
	}{
		{name: "signed in, consenting", existing: none, signedIn: true, consent: true,
			wantRow: true, wantOwned: true, wantConsent: true, wantChange: true},
		{name: "signed in, not consenting", existing: none, signedIn: true, consent: false,
			wantRow: true, wantOwned: true, wantConsent: false},
		{name: "signed in takes over an anonymous row", existing: anonymous, signedIn: true, consent: true,
			wantRow: true, wantOwned: true, wantConsent: true},
		{name: "anonymous, consenting, new", existing: none, signedIn: false, consent: true,
			wantRow: true, wantOwned: false, wantConsent: true, wantChange: true},
		{name: "anonymous, consenting, keeps the owner", existing: owned, signedIn: false, consent: true,
			wantRow: true, wantOwned: true, wantConsent: true},
		{name: "anonymous, withdrawing, deletes an anonymous row", existing: anonymous, signedIn: false, consent: false,
			wantRow: false, wantChange: true},
		{name: "anonymous, withdrawing, keeps an owned row", existing: owned, signedIn: false, consent: false,
			wantRow: true, wantOwned: true, wantConsent: false, wantChange: true},
		{name: "anonymous, not consenting, no row", existing: none, signedIn: false, consent: false,
			wantRow: false},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			// arrange
			d := tdb.New(t)
			uc := usecase.NewRegisterDevice(d.Infra())
			ctx := clock.Set(t.Context(), clock.NewFixed(now))
			userID := d.Seeder.User(t)
			dev := fixture.Device()
			switch tt.existing {
			case none:
			case anonymous:
				dev.PromotionsConsent = true
				require.NoError(t, uc.Do(ctx, usecase.RegisterDeviceInput{Device: dev}))
			case owned:
				dev.PromotionsConsent = true
				require.NoError(t, uc.Do(ctx, usecase.RegisterDeviceInput{UserID: &userID, Device: dev}))
			}
			changesBefore := countConsentChanges(t, d)
			in := usecase.RegisterDeviceInput{Device: dev}
			in.Device.PromotionsConsent = tt.consent
			if tt.signedIn {
				in.UserID = &userID
			}

			// act
			err := uc.Do(ctx, in)

			// assert
			require.NoError(t, err)
			if tt.wantChange {
				require.Equal(t, changesBefore+1, countConsentChanges(t, d))
				change := latestConsentChange(t, d)
				assert.Equal(t, tt.consent, change.Consented)
				assert.True(t, now.Equal(change.ChangedAt))
				if tt.wantOwned {
					assert.Equal(t, &userID, change.UserID)
				} else {
					assert.Nil(t, change.UserID)
				}
			} else {
				assert.Equal(t, changesBefore, countConsentChanges(t, d))
			}
			row, ok := findDevice(t, d, dev)
			require.Equal(t, tt.wantRow, ok)
			if !tt.wantRow {
				return
			}
			if tt.wantOwned {
				assert.Equal(t, &userID, row.userID)
			} else {
				assert.Nil(t, row.userID)
			}
			assert.Equal(t, tt.wantConsent, row.promotionsConsent)
		})
	}
}

func TestRegisterDevice_language(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	uc := usecase.NewRegisterDevice(d.Infra())
	userID := d.Seeder.User(t)
	dev := fixture.Device(func(m *model.Device) { m.Language = model.LanguageEnglish })
	require.NoError(t, uc.Do(t.Context(), usecase.RegisterDeviceInput{UserID: &userID, Device: dev}))

	// act
	dev.Language = model.LanguageJapanese
	err := uc.Do(t.Context(), usecase.RegisterDeviceInput{UserID: &userID, Device: dev})

	// assert
	require.NoError(t, err)
	row, ok := findDevice(t, d, dev)
	require.True(t, ok)
	assert.Equal(t, "ja", row.language)
}

type deviceRow struct {
	userID            *uuid.UUID
	language          string
	promotionsConsent bool
	lastSeenAt        time.Time
}

func findDevice(t *testing.T, d tdb.DB, dev model.Device) (deviceRow, bool) {
	t.Helper()

	var row deviceRow
	err := d.Writer.QueryRow(t.Context(),
		`SELECT user_id, language, promotions_consented_at IS NOT NULL, last_seen_at
		 FROM devices WHERE platform = $1 AND push_token = $2`,
		string(dev.Platform), dev.PushToken).Scan(&row.userID, &row.language, &row.promotionsConsent, &row.lastSeenAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return deviceRow{}, false
	}
	require.NoError(t, err)
	return row, true
}

func countConsentChanges(t *testing.T, d tdb.DB) int {
	t.Helper()

	var n int
	require.NoError(t, d.Writer.QueryRow(t.Context(), "SELECT count(*) FROM promotions_consent_changes").Scan(&n))
	return n
}

func latestConsentChange(t *testing.T, d tdb.DB) model.PromotionsConsentChange {
	t.Helper()

	var c model.PromotionsConsentChange
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"SELECT device_id, user_id, consented, changed_at FROM promotions_consent_changes ORDER BY id DESC LIMIT 1").
		Scan(&c.DeviceID, &c.UserID, &c.Consented, &c.ChangedAt))
	return c
}
