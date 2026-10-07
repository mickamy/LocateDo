package usecase_test

import (
	"testing"
	"time"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/campaign/model"
	"github.com/mickamy/LocateDo/internal/feature/campaign/usecase"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/test/tdb"
)

var now = time.Date(2026, 10, 7, 3, 0, 0, 0, time.UTC)

func TestSendCampaign(t *testing.T) {
	t.Parallel()

	// arrange: two consenting Japanese devices, one not consenting, one English
	d := tdb.New(t)
	device(t, d, "ios", "ja", true)
	device(t, d, "android", "ja", true)
	device(t, d, "ios", "ja", false)
	device(t, d, "ios", "en", true)
	ctx := clock.Set(t.Context(), clock.NewFixed(now))

	// act
	out, err := usecase.NewSendCampaign(d.Infra()).Do(ctx, usecase.SendCampaignInput{Campaign: campaign()})

	// assert
	require.NoError(t, err)
	assert.Equal(t, model.Audience{IOS: 1, Android: 1}, out.Audience)
	var language, title string
	var url *string
	var target int
	var createdAt time.Time
	var sentAt *time.Time
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"SELECT language, title, url, target_count, created_at, sent_at FROM campaigns WHERE id = $1", out.CampaignID).
		Scan(&language, &title, &url, &target, &createdAt, &sentAt))
	assert.Equal(t, "ja", language)
	assert.Equal(t, "New", title)
	assert.Nil(t, url)
	assert.Equal(t, 2, target)
	assert.True(t, now.Equal(createdAt))
	assert.Nil(t, sentAt)
	var payload string
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"SELECT payload->>'campaign_id' FROM outbox_messages WHERE kind = 'send_campaign' AND dedupe_key = $1",
		"campaign:"+out.CampaignID.String()).Scan(&payload))
	assert.Equal(t, out.CampaignID.String(), payload)
}

func TestSendCampaign_dryRunWritesNothing(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	device(t, d, "ios", "ja", true)
	ctx := clock.Set(t.Context(), clock.NewFixed(now))

	// act
	out, err := usecase.NewSendCampaign(d.Infra()).Do(ctx, usecase.SendCampaignInput{Campaign: campaign(), DryRun: true})

	// assert
	require.NoError(t, err)
	assert.Equal(t, model.Audience{IOS: 1}, out.Audience)
	assert.Equal(t, uuid.UUID{}, out.CampaignID)
	assert.Equal(t, 0, count(t, d, "SELECT count(*) FROM campaigns"))
	assert.Equal(t, 0, count(t, d, "SELECT count(*) FROM outbox_messages WHERE kind = 'send_campaign'"))
}

func TestSendCampaign_interval(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name     string
		language string
		sentAgo  time.Duration
		force    bool
		ok       bool
	}{
		{name: "within 14 days", language: "ja", sentAgo: 13 * 24 * time.Hour},
		{name: "within 14 days, forced", language: "ja", sentAgo: 13 * 24 * time.Hour, force: true, ok: true},
		{name: "after 14 days", language: "ja", sentAgo: 14 * 24 * time.Hour, ok: true},
		{name: "another language", language: "en", sentAgo: time.Hour, ok: true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			// arrange
			d := tdb.New(t)
			sent(t, d, tt.language, now.Add(-tt.sentAgo))
			ctx := clock.Set(t.Context(), clock.NewFixed(now))

			// act
			in := usecase.SendCampaignInput{Campaign: campaign(), Force: tt.force}
			_, err := usecase.NewSendCampaign(d.Infra()).Do(ctx, in)

			// assert
			if tt.ok {
				require.NoError(t, err)
				return
			}
			require.ErrorIs(t, err, aerrors.ErrPrecondition)
			assert.Equal(t, 1, count(t, d, "SELECT count(*) FROM campaigns"))
		})
	}
}

func TestSendCampaign_unsentCampaignBlocksEvenWhenForced(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	ctx := clock.Set(t.Context(), clock.NewFixed(now))
	uc := usecase.NewSendCampaign(d.Infra())
	_, err := uc.Do(ctx, usecase.SendCampaignInput{Campaign: campaign()})
	require.NoError(t, err)

	// act
	_, err = uc.Do(ctx, usecase.SendCampaignInput{Campaign: campaign(), Force: true})

	// assert
	require.ErrorIs(t, err, aerrors.ErrPrecondition)
}

func TestSendCampaign_invalid(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	c := campaign()
	c.URL = "http://locatedo.com"

	// act
	_, err := usecase.NewSendCampaign(d.Infra()).Do(t.Context(), usecase.SendCampaignInput{Campaign: c, DryRun: true})

	// assert
	require.ErrorIs(t, err, aerrors.ErrInvalidArgument)
}

func campaign() model.Campaign {
	return model.Campaign{Language: "ja", Title: "New", Body: "Repeating to-dos are here"}
}

func device(t *testing.T, d tdb.DB, platform, language string, consenting bool) {
	t.Helper()

	var consentedAt *time.Time
	if consenting {
		consentedAt = &now
	}
	var env *string
	if platform == "ios" {
		env = new("production")
	}
	_, err := d.Writer.Exec(t.Context(),
		`INSERT INTO devices (platform, push_token, apns_environment, language, promotions_consented_at)
		 VALUES ($1, $2, $3, $4, $5)`,
		platform, uuid.NewV7().String(), env, language, consentedAt)
	require.NoError(t, err)
}

func sent(t *testing.T, d tdb.DB, language string, at time.Time) {
	t.Helper()

	_, err := d.Writer.Exec(t.Context(),
		"INSERT INTO campaigns (language, title, body, target_count, sent_at) VALUES ($1, 't', 'b', 0, $2)",
		language, at)
	require.NoError(t, err)
}

func count(t *testing.T, d tdb.DB, sql string) int {
	t.Helper()

	var n int
	require.NoError(t, d.Writer.QueryRow(t.Context(), sql).Scan(&n))
	return n
}
