package job_test

import (
	"errors"
	"fmt"
	"testing"
	"time"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/infra/apns"
	"github.com/mickamy/LocateDo/internal/infra/fcm"
	"github.com/mickamy/LocateDo/internal/outbox"
	"github.com/mickamy/LocateDo/internal/worker/job"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestSendCampaign_sendsToConsentingDevicesInItsLanguage(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	campaignID := campaignRow(t, d, "ja", "https://locatedo.com/news")
	consentingDevice(t, d, "ios", "production", "ja-phone", "ja")
	consentingDevice(t, d, "android", "", "ja-android", "ja")
	consentingDevice(t, d, "ios", "production", "en-phone", "en")
	device(t, d, d.Seeder.User(t), "ios", "production", "not-consenting")
	ios := &fakePusher{}
	android := &fakeFCM{}

	// act
	err := campaignJob(d, ios, android).Handle(t.Context(), campaignMessage(campaignID, 0))

	// assert
	require.NoError(t, err)
	assert.Equal(t, []string{"ja-phone"}, ios.promotedTokens())
	assert.Equal(t, []string{"ja-android"}, android.promotedTokens())
	assert.Equal(t, apns.Promotion{
		CampaignID: campaignID, Title: "New", Body: "Try it", URL: "https://locatedo.com/news",
	}, ios.promotion("ja-phone"))
	assert.Equal(t, fcm.Promotion{
		CampaignID: campaignID, Title: "New", Body: "Try it", URL: "https://locatedo.com/news",
	}, android.promotion("ja-android"))
	got := campaignCounts(t, d, campaignID)
	assert.True(t, got.sent)
	assert.Equal(t, 2, got.sentCount)
	assert.Equal(t, 0, got.unregisteredCount)
	assert.Equal(t, 0, got.failedCount)
	assert.Equal(t, 0, got.uncertainCount)
	assert.Equal(t, 2, rows(t, d,
		"SELECT count(*) FROM campaign_deliveries WHERE campaign_id = $1 AND sent_at IS NOT NULL", campaignID))
}

func TestSendCampaign_retryDoesNotSendTwice(t *testing.T) {
	t.Parallel()

	// arrange: one device fails the first attempt
	d := tdb.New(t)
	campaignID := campaignRow(t, d, "ja", "")
	consentingDevice(t, d, "ios", "production", "fine", "ja")
	consentingDevice(t, d, "ios", "production", "flaky", "ja")
	ios := &fakePusher{fail: map[string]error{"flaky": fmt.Errorf("%w: status 503", apns.ErrNotDelivered)}}
	sendJob := campaignJob(d, ios, &fakeFCM{})
	require.Error(t, sendJob.Handle(t.Context(), campaignMessage(campaignID, 0)))
	assert.False(t, campaignCounts(t, d, campaignID).sent, "a failure leaves the campaign unsent")

	// act
	ios.fail = nil
	err := sendJob.Handle(t.Context(), campaignMessage(campaignID, 1))

	// assert
	require.NoError(t, err)
	assert.Equal(t, []string{"fine", "flaky"}, ios.promotedTokens(), "the retry only tries the device it missed")
	got := campaignCounts(t, d, campaignID)
	assert.True(t, got.sent)
	assert.Equal(t, 2, got.sentCount)
	assert.Equal(t, 0, got.failedCount)
}

func TestSendCampaign_givesUpOnTheLastAttempt(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	campaignID := campaignRow(t, d, "ja", "")
	consentingDevice(t, d, "ios", "production", "fine", "ja")
	consentingDevice(t, d, "android", "", "broken", "ja")
	android := &fakeFCM{fail: map[string]error{"broken": fmt.Errorf("%w: status 503", fcm.ErrNotDelivered)}}

	// act: the fifth attempt
	err := campaignJob(d, &fakePusher{}, android).Handle(t.Context(), campaignMessage(campaignID, 4))

	// assert
	require.NoError(t, err)
	got := campaignCounts(t, d, campaignID)
	assert.True(t, got.sent, "the next campaign in the language is no longer blocked")
	assert.Equal(t, 1, got.sentCount)
	assert.Equal(t, 1, got.failedCount)
	assert.Equal(t, 0, got.uncertainCount)
}

func TestSendCampaign_neverRepeatsAPushOfUnknownOutcome(t *testing.T) {
	t.Parallel()

	// arrange: the connection drops before APNs answers
	d := tdb.New(t)
	campaignID := campaignRow(t, d, "ja", "")
	consentingDevice(t, d, "ios", "production", "fine", "ja")
	consentingDevice(t, d, "ios", "production", "dropped", "ja")
	ios := &fakePusher{fail: map[string]error{"dropped": errors.New("connection reset")}}
	sendJob := campaignJob(d, ios, &fakeFCM{})

	// act
	err := sendJob.Handle(t.Context(), campaignMessage(campaignID, 0))

	// assert
	require.NoError(t, err)
	got := campaignCounts(t, d, campaignID)
	assert.True(t, got.sent, "nothing is left to retry")
	assert.Equal(t, 1, got.sentCount)
	assert.Equal(t, 0, got.failedCount)
	assert.Equal(t, 1, got.uncertainCount)
}

func TestSendCampaign_skipsDevicesAlreadyTried(t *testing.T) {
	t.Parallel()

	// arrange: a worker died after claiming the device, before marking it sent
	d := tdb.New(t)
	campaignID := campaignRow(t, d, "ja", "")
	consentingDevice(t, d, "ios", "production", "claimed", "ja")
	_, err := d.Writer.Exec(t.Context(),
		`INSERT INTO campaign_deliveries (campaign_id, device_id, attempted_at)
		 SELECT $1, id, now() FROM devices WHERE push_token = 'claimed'`, campaignID)
	require.NoError(t, err)
	ios := &fakePusher{}

	// act
	err = campaignJob(d, ios, &fakeFCM{}).Handle(t.Context(), campaignMessage(campaignID, 1))

	// assert
	require.NoError(t, err)
	assert.Empty(t, ios.promotedTokens())
	got := campaignCounts(t, d, campaignID)
	assert.Equal(t, 0, got.sentCount)
	assert.Equal(t, 1, got.uncertainCount)
}

func TestSendCampaign_forgetsUnregisteredDevices(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	campaignID := campaignRow(t, d, "en", "")
	consentingDevice(t, d, "ios", "production", "gone-phone", "en")
	consentingDevice(t, d, "android", "", "gone-android", "en")
	ios := &fakePusher{fail: map[string]error{"gone-phone": apns.ErrUnregistered}}
	android := &fakeFCM{fail: map[string]error{"gone-android": fcm.ErrUnregistered}}

	// act
	err := campaignJob(d, ios, android).Handle(t.Context(), campaignMessage(campaignID, 0))

	// assert
	require.NoError(t, err)
	assert.Empty(t, tokens(t, d))
	got := campaignCounts(t, d, campaignID)
	assert.True(t, got.sent)
	assert.Equal(t, 0, got.sentCount)
	assert.Equal(t, 2, got.unregisteredCount)
}

func TestSendCampaign_pagesThroughEveryDevice(t *testing.T) {
	t.Parallel()

	// arrange: more devices than one page
	d := tdb.New(t)
	campaignID := campaignRow(t, d, "ja", "")
	_, err := d.Writer.Exec(t.Context(),
		`INSERT INTO devices (platform, push_token, language, promotions_consented_at)
		 SELECT 'android', 'fid-' || n, 'ja', now() FROM generate_series(1, 501) AS n`)
	require.NoError(t, err)
	android := &fakeFCM{}

	// act
	err = campaignJob(d, &fakePusher{}, android).Handle(t.Context(), campaignMessage(campaignID, 0))

	// assert
	require.NoError(t, err)
	assert.Len(t, android.promotedTokens(), 501)
	assert.Equal(t, 501, campaignCounts(t, d, campaignID).sentCount)
}

func TestSendCampaign_sentCampaignIsLeftAlone(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	campaignID := campaignRow(t, d, "ja", "")
	_, err := d.Writer.Exec(t.Context(), "UPDATE campaigns SET sent_at = now() WHERE id = $1", campaignID)
	require.NoError(t, err)
	consentingDevice(t, d, "ios", "production", "ja-phone", "ja")
	ios := &fakePusher{}

	// act
	err = campaignJob(d, ios, &fakeFCM{}).Handle(t.Context(), campaignMessage(campaignID, 0))

	// assert
	require.NoError(t, err)
	assert.Empty(t, ios.promotedTokens())
}

func campaignJob(d tdb.DB, ios apns.Pusher, android fcm.Pusher) *job.SendCampaign {
	infra := d.Infra()
	infra.APNs = ios
	infra.FCM = android
	return job.NewSendCampaign(infra)
}

func campaignMessage(campaignID uuid.UUID, attempts int32) outbox.Message {
	m := outbox.SendCampaign(campaignID, time.Now())
	m.Attempts = attempts
	return m
}

func campaignRow(t *testing.T, d tdb.DB, language, url string) uuid.UUID {
	t.Helper()

	var id uuid.UUID
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		`INSERT INTO campaigns (language, title, body, url, target_count)
		 VALUES ($1, 'New', 'Try it', NULLIF($2, ''), 0) RETURNING id`, language, url).Scan(&id))
	return id
}

func consentingDevice(t *testing.T, d tdb.DB, platform, env, token, language string) {
	t.Helper()

	_, err := d.Writer.Exec(t.Context(),
		`INSERT INTO devices (platform, apns_environment, push_token, language, promotions_consented_at)
		 VALUES ($1, NULLIF($2, ''), $3, $4, now())`, platform, env, token, language)
	require.NoError(t, err)
}

type campaignState struct {
	sent              bool
	sentCount         int
	unregisteredCount int
	failedCount       int
	uncertainCount    int
}

func campaignCounts(t *testing.T, d tdb.DB, id uuid.UUID) campaignState {
	t.Helper()

	var s campaignState
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		`SELECT sent_at IS NOT NULL, COALESCE(sent_count, 0), COALESCE(unregistered_count, 0),
		        COALESCE(failed_count, 0), COALESCE(uncertain_count, 0)
		 FROM campaigns WHERE id = $1`, id).
		Scan(&s.sent, &s.sentCount, &s.unregisteredCount, &s.failedCount, &s.uncertainCount))
	return s
}
