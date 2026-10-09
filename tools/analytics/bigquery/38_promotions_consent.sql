-- Who gets asked for promotional-push consent after their first arrival notification and how they answer,
-- by the ISO week (Monday start) users started and platform.
WITH per_user AS (
  SELECT
    u.user_pseudo_id,
    u.first_date,
    u.platform,
    MIN(IF(e.event_name = 'arrival_notified', e.event_date, NULL)) AS first_arrival_date,
    LOGICAL_OR(e.event_name = 'promotions_prompt_shown') AS prompted,
    MAX(IF(e.event_name = 'promotions_prompt_answered', e.result, NULL)) AS answer,
    LOGICAL_OR(
      e.event_name = 'promotions_consent_changed' AND e.source = 'settings' AND e.auth_to = 'on'
    ) AS turned_on_in_settings,
    LOGICAL_OR(
      e.event_name = 'promotions_consent_changed' AND e.source = 'settings' AND e.auth_to = 'off'
    ) AS turned_off_in_settings,
    ARRAY_AGG(
      IF(e.event_name = 'daily_state', e.promotions_consent, NULL) IGNORE NULLS ORDER BY e.event_time DESC LIMIT 1
    )[SAFE_OFFSET(0)] AS latest_consent
  FROM `__PROJECT__.__DATASET__.users` AS u
  JOIN `__PROJECT__.__DATASET__.events` AS e USING (user_pseudo_id)
  GROUP BY u.user_pseudo_id, u.first_date, u.platform
)
SELECT
  DATE_TRUNC(first_date, WEEK(MONDAY)) AS cohort_week,
  platform,
  COUNT(*) AS users,
  COUNTIF(first_arrival_date IS NOT NULL) AS reached_first_arrival,
  SAFE_DIVIDE(COUNTIF(first_arrival_date IS NOT NULL), COUNT(*)) AS first_arrival_rate,
  APPROX_QUANTILES(DATE_DIFF(first_arrival_date, first_date, DAY), 2 IGNORE NULLS)[SAFE_OFFSET(1)]
    AS median_days_to_first_arrival,
  COUNTIF(prompted) AS prompted,
  COUNTIF(answer = 'accepted') AS accepted,
  COUNTIF(answer = 'declined') AS declined,
  COUNTIF(answer = 'dismissed') AS dismissed,
  SAFE_DIVIDE(COUNTIF(answer = 'accepted'), COUNTIF(prompted)) AS acceptance_rate,
  COUNTIF(turned_on_in_settings) AS turned_on_in_settings,
  COUNTIF(turned_off_in_settings) AS turned_off_in_settings,
  COUNTIF(latest_consent = 1) AS consenting_now,
  SAFE_DIVIDE(COUNTIF(latest_consent = 1), COUNT(*)) AS consenting_rate
FROM per_user
GROUP BY cohort_week, platform
