-- One row per promotional push that someone opened and platform: who could receive it, who opened it, what openers did within
-- a day, and how many recipients opted out in the week after it compared with the week before.
-- The send time comes from the campaign id, a UUID v7 whose first 48 bits are Unix milliseconds.
-- Recipients are users whose latest daily_state in the 30 days up to the send had consent on and the same language.
WITH opens AS (
  SELECT
    campaign_id,
    user_pseudo_id,
    ANY_VALUE(platform) AS platform,
    MIN(event_time) AS opened_at,
    MIN(latency_s) AS latency_s,
    MAX(has_url) AS has_url,
    ANY_VALUE(IF(STARTS_WITH(LOWER(language), 'ja'), 'ja', 'en')) AS app_language
  FROM `__PROJECT__.__DATASET__.events`
  WHERE event_name = 'campaign_opened' AND campaign_id IS NOT NULL
  GROUP BY campaign_id, user_pseudo_id
),
campaigns AS (
  SELECT
    campaign_id,
    TIMESTAMP_MILLIS(CAST(CONCAT('0x', SUBSTR(REPLACE(campaign_id, '-', ''), 1, 12)) AS INT64)) AS sent_at,
    APPROX_TOP_COUNT(app_language, 1)[OFFSET(0)].value AS language,
    MAX(has_url) = 1 AS has_url
  FROM opens
  GROUP BY campaign_id
),
recipients AS (
  SELECT c.campaign_id, d.platform, d.user_pseudo_id
  FROM campaigns AS c
  JOIN `__PROJECT__.__DATASET__.daily_state` AS d
    ON d.event_date BETWEEN DATE_SUB(DATE(c.sent_at), INTERVAL 30 DAY) AND DATE(c.sent_at)
  WHERE TRUE
  QUALIFY ROW_NUMBER() OVER (PARTITION BY c.campaign_id, d.user_pseudo_id ORDER BY d.event_date DESC) = 1
    AND d.promotions_consent
    AND IF(STARTS_WITH(LOWER(d.language), 'ja'), 'ja', 'en') = c.language
),
openers AS (
  SELECT
    campaign_id,
    platform,
    COUNT(*) AS openers,
    APPROX_QUANTILES(latency_s, 2)[SAFE_OFFSET(1)] AS median_latency_s
  FROM opens
  GROUP BY campaign_id, platform
),
after_open AS (
  SELECT
    o.campaign_id,
    o.platform,
    COUNT(DISTINCT IF(e.event_name = 'paywall_shown', o.user_pseudo_id, NULL)) AS paywall_shown_24h,
    COUNT(DISTINCT IF(e.event_name = 'paywall_purchased', o.user_pseudo_id, NULL)) AS purchased_24h
  FROM opens AS o
  JOIN `__PROJECT__.__DATASET__.events` AS e
    ON e.user_pseudo_id = o.user_pseudo_id
    AND e.event_time BETWEEN o.opened_at AND TIMESTAMP_ADD(o.opened_at, INTERVAL 24 HOUR)
  WHERE e.event_name IN ('paywall_shown', 'paywall_purchased')
  GROUP BY o.campaign_id, o.platform
),
opt_outs AS (
  SELECT
    r.campaign_id,
    r.platform,
    COUNT(DISTINCT IF(
      e.event_name = 'promotions_consent_changed' AND e.event_time >= c.sent_at, r.user_pseudo_id, NULL
    )) AS consent_off_week_after,
    COUNT(DISTINCT IF(
      e.event_name = 'promotions_consent_changed' AND e.event_time < c.sent_at, r.user_pseudo_id, NULL
    )) AS consent_off_week_before,
    COUNT(DISTINCT IF(
      e.event_name = 'notification_auth_changed' AND e.event_time >= c.sent_at, r.user_pseudo_id, NULL
    )) AS notifications_denied_week_after,
    COUNT(DISTINCT IF(
      e.event_name = 'notification_auth_changed' AND e.event_time < c.sent_at, r.user_pseudo_id, NULL
    )) AS notifications_denied_week_before
  FROM recipients AS r
  JOIN campaigns AS c USING (campaign_id)
  JOIN `__PROJECT__.__DATASET__.events` AS e
    ON e.user_pseudo_id = r.user_pseudo_id
    AND e.event_time BETWEEN TIMESTAMP_SUB(c.sent_at, INTERVAL 7 DAY) AND TIMESTAMP_ADD(c.sent_at, INTERVAL 7 DAY)
  WHERE (e.event_name = 'promotions_consent_changed' AND e.auth_to = 'off')
    OR (e.event_name = 'notification_auth_changed' AND e.auth_to = 'denied')
  GROUP BY r.campaign_id, r.platform
),
recipient_counts AS (
  SELECT campaign_id, platform, COUNT(*) AS recipients
  FROM recipients
  GROUP BY campaign_id, platform
),
campaign_platforms AS (
  SELECT campaign_id, platform FROM openers
  UNION DISTINCT
  SELECT campaign_id, platform FROM recipient_counts
)
SELECT
  c.campaign_id,
  k.platform,
  c.sent_at,
  c.language,
  c.has_url,
  COALESCE(rc.recipients, 0) AS recipients,
  COALESCE(op.openers, 0) AS openers,
  SAFE_DIVIDE(op.openers, rc.recipients) AS open_rate,
  op.median_latency_s,
  COALESCE(a.paywall_shown_24h, 0) AS paywall_shown_24h,
  COALESCE(a.purchased_24h, 0) AS purchased_24h,
  COALESCE(o.consent_off_week_after, 0) AS consent_off_week_after,
  COALESCE(o.consent_off_week_before, 0) AS consent_off_week_before,
  COALESCE(o.notifications_denied_week_after, 0) AS notifications_denied_week_after,
  COALESCE(o.notifications_denied_week_before, 0) AS notifications_denied_week_before
FROM campaigns AS c
JOIN campaign_platforms AS k USING (campaign_id)
LEFT JOIN openers AS op USING (campaign_id, platform)
LEFT JOIN recipient_counts AS rc USING (campaign_id, platform)
LEFT JOIN after_open AS a USING (campaign_id, platform)
LEFT JOIN opt_outs AS o USING (campaign_id, platform)
