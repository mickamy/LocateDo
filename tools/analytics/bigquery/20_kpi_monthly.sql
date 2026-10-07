-- The four KPIs from business-spec, one row per month.
-- Retention: users who started in the month and got an arrival reminder 7-13 days later (cohorts at least 14 days old).
-- Share taps: users who tapped share / users who opened the app in the month.
-- Trial conversion: trials started in the month that later converted (rc_* events come from RevenueCat), counting only
--   trials started at least 8 days ago so the 7-day trial has ended.
--   The _unaffected columns leave out users whose sync was held back by a stale server plan (see sync_blocked_weekly).
-- Paid users: users whose latest subscription event by the end of the month is not an expiration.
WITH months AS (
  SELECT month
  FROM UNNEST(GENERATE_DATE_ARRAY(
    (SELECT DATE_TRUNC(MIN(event_date), MONTH) FROM `__PROJECT__.__DATASET__.events`),
    DATE_TRUNC(CURRENT_DATE(), MONTH),
    INTERVAL 1 MONTH
  )) AS month
),
retention AS (
  SELECT
    DATE_TRUNC(first_date, MONTH) AS month,
    COUNT(*) AS cohort_users,
    COUNTIF(notified_week_2) AS notified_week_2_users
  FROM `__PROJECT__.__DATASET__.users`
  WHERE days_since_first >= 14
  GROUP BY month
),
share AS (
  SELECT
    month,
    COUNT(*) AS monthly_active_users,
    COUNTIF(tapped_share) AS share_tappers
  FROM (
    SELECT
      DATE_TRUNC(event_date, MONTH) AS month,
      user_pseudo_id,
      LOGICAL_OR(event_name = 'share_tapped') AS tapped_share
    FROM `__PROJECT__.__DATASET__.events`
    GROUP BY month, user_pseudo_id
    HAVING LOGICAL_OR(foreground)
  )
  GROUP BY month
),
trials AS (
  SELECT
    user_pseudo_id,
    MIN(event_time) AS started_at,
    MIN(event_time) <= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 8 DAY) AS ended
  FROM `__PROJECT__.__DATASET__.events`
  WHERE event_name = 'rc_trial_started_event'
  GROUP BY user_pseudo_id
),
converted AS (
  SELECT DISTINCT t.user_pseudo_id
  FROM trials AS t
  JOIN `__PROJECT__.__DATASET__.events` AS e
    ON e.user_pseudo_id = t.user_pseudo_id
    AND e.event_name = 'rc_trial_converted_event'
    AND e.event_time >= t.started_at
),
conversion AS (
  SELECT
    DATE_TRUNC(DATE(t.started_at), MONTH) AS month,
    COUNT(*) AS trials_started,
    COUNTIF(t.ended) AS trials_ended,
    COUNTIF(t.ended AND c.user_pseudo_id IS NOT NULL) AS trials_converted,
    COUNTIF(t.ended AND NOT COALESCE(u.blocked_by_plan, FALSE)) AS trials_ended_unaffected,
    COUNTIF(t.ended AND c.user_pseudo_id IS NOT NULL AND NOT COALESCE(u.blocked_by_plan, FALSE))
      AS trials_converted_unaffected
  FROM trials AS t
  LEFT JOIN converted AS c USING (user_pseudo_id)
  LEFT JOIN `__PROJECT__.__DATASET__.users` AS u USING (user_pseudo_id)
  GROUP BY month
),
lifecycle AS (
  SELECT
    user_pseudo_id,
    event_date,
    event_time,
    event_name != 'rc_expiration_event' AS is_paid
  FROM `__PROJECT__.__DATASET__.events`
  WHERE event_name IN (
    'rc_initial_purchase_event',
    'rc_trial_converted_event',
    'rc_renewal_event',
    'rc_uncancellation_event',
    'rc_product_change_event',
    'rc_expiration_event'
  )
),
paid AS (
  SELECT month, COUNTIF(is_paid) AS paid_users
  FROM (
    SELECT m.month, l.user_pseudo_id, l.is_paid
    FROM months AS m
    JOIN lifecycle AS l ON l.event_date <= LAST_DAY(m.month)
    QUALIFY ROW_NUMBER() OVER (PARTITION BY m.month, l.user_pseudo_id ORDER BY l.event_time DESC) = 1
  )
  GROUP BY month
)
SELECT
  m.month,
  r.cohort_users,
  r.notified_week_2_users,
  SAFE_DIVIDE(r.notified_week_2_users, r.cohort_users) AS retention_week_2,
  s.monthly_active_users,
  s.share_tappers,
  SAFE_DIVIDE(s.share_tappers, s.monthly_active_users) AS share_tap_rate,
  c.trials_started,
  c.trials_ended,
  c.trials_converted,
  SAFE_DIVIDE(c.trials_converted, c.trials_ended) AS trial_conversion_rate,
  c.trials_ended_unaffected,
  c.trials_converted_unaffected,
  SAFE_DIVIDE(c.trials_converted_unaffected, c.trials_ended_unaffected) AS trial_conversion_rate_unaffected,
  COALESCE(p.paid_users, 0) AS paid_users,
  SAFE_DIVIDE(
    COALESCE(p.paid_users, 0) - LAG(COALESCE(p.paid_users, 0)) OVER (ORDER BY m.month),
    LAG(COALESCE(p.paid_users, 0)) OVER (ORDER BY m.month)
  ) AS paid_users_growth
FROM months AS m
LEFT JOIN retention AS r USING (month)
LEFT JOIN share AS s USING (month)
LEFT JOIN conversion AS c USING (month)
LEFT JOIN paid AS p USING (month)
