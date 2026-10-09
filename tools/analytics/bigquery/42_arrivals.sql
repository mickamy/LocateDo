-- Arrivals the app noticed and why some were not announced, by month and platform, to tell "arrivals are not detected" from
-- "there was nothing to remind".
-- users_waiting_without_arrivals: users whose daily_state that month showed a place with open to-dos but who had no
-- arrival at all, the ones the geofence may be missing.
-- App versions before arrival_suppressed log only reminders, and also logged them with notifications off.
WITH arrivals AS (
  SELECT
    DATE_TRUNC(event_date, MONTH) AS month,
    platform,
    COUNT(*) AS arrivals,
    COUNTIF(event_name = 'arrival_notified') AS reminders,
    COUNTIF(event_name = 'arrival_suppressed') AS suppressed,
    COUNTIF(reason = 'no_open_todos') AS suppressed_no_open_todos,
    COUNTIF(reason = 'assigned_to_others') AS suppressed_assigned_to_others,
    COUNTIF(reason = 'recently_notified') AS suppressed_recently_notified,
    COUNTIF(reason = 'notifications_off') AS suppressed_notifications_off,
    COUNT(DISTINCT user_pseudo_id) AS users_arrived,
    COUNT(DISTINCT IF(event_name = 'arrival_notified', user_pseudo_id, NULL)) AS users_reminded
  FROM `__PROJECT__.__DATASET__.events`
  WHERE event_name IN ('arrival_notified', 'arrival_suppressed')
  GROUP BY month, platform
),
waiting_users AS (
  SELECT DISTINCT DATE_TRUNC(event_date, MONTH) AS month, platform, user_pseudo_id
  FROM `__PROJECT__.__DATASET__.daily_state`
  WHERE places_with_open_todos > 0
),
arrived_users AS (
  SELECT DISTINCT DATE_TRUNC(event_date, MONTH) AS month, user_pseudo_id
  FROM `__PROJECT__.__DATASET__.events`
  WHERE event_name IN ('arrival_notified', 'arrival_suppressed')
),
waiting AS (
  SELECT
    w.month,
    w.platform,
    COUNT(*) AS users_waiting,
    COUNTIF(a.user_pseudo_id IS NULL) AS users_waiting_without_arrivals
  FROM waiting_users AS w
  LEFT JOIN arrived_users AS a USING (month, user_pseudo_id)
  GROUP BY w.month, w.platform
)
SELECT
  month,
  platform,
  COALESCE(a.arrivals, 0) AS arrivals,
  COALESCE(a.reminders, 0) AS reminders,
  SAFE_DIVIDE(a.reminders, a.arrivals) AS reminder_rate,
  COALESCE(a.suppressed, 0) AS suppressed,
  COALESCE(a.suppressed_no_open_todos, 0) AS suppressed_no_open_todos,
  COALESCE(a.suppressed_assigned_to_others, 0) AS suppressed_assigned_to_others,
  COALESCE(a.suppressed_recently_notified, 0) AS suppressed_recently_notified,
  COALESCE(a.suppressed_notifications_off, 0) AS suppressed_notifications_off,
  COALESCE(a.users_arrived, 0) AS users_arrived,
  COALESCE(a.users_reminded, 0) AS users_reminded,
  COALESCE(w.users_waiting, 0) AS users_waiting,
  COALESCE(w.users_waiting_without_arrivals, 0) AS users_waiting_without_arrivals,
  SAFE_DIVIDE(w.users_waiting_without_arrivals, w.users_waiting) AS waiting_without_arrivals_rate
FROM arrivals AS a
FULL JOIN waiting AS w USING (month, platform)
