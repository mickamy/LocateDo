-- Arrivals and departures the app noticed and why some were not announced, by month, platform, and kind, to tell
-- "they are not detected" from "there was nothing to remind".
-- suppressed_schedule_failed: the system refused a notification the app meant to show, so nobody saw it.
-- suppressed_short_stay: left within 5 minutes of entering (departures only); short_stays_with_todos are the ones
-- that had departure to-dos waiting, and median_short_stay_min how long those stays were.
-- users_waiting_without_detection: users whose daily_state that month showed to-dos of that kind waiting (any open
-- to-do for arrivals, departure to-dos for departures) but who had no such event at all, the ones the geofence may be
-- missing.
WITH detected AS (
  SELECT
    DATE_TRUNC(event_date, MONTH) AS month,
    platform,
    place_event,
    COUNT(*) AS detected,
    COUNTIF(event_name = 'reminder_notified') AS reminders,
    COUNTIF(event_name = 'reminder_suppressed') AS suppressed,
    COUNTIF(reason = 'no_open_todos') AS suppressed_no_open_todos,
    COUNTIF(reason = 'assigned_to_others') AS suppressed_assigned_to_others,
    COUNTIF(reason = 'recently_notified') AS suppressed_recently_notified,
    COUNTIF(reason = 'notifications_off') AS suppressed_notifications_off,
    COUNTIF(reason = 'schedule_failed') AS suppressed_schedule_failed,
    COUNTIF(reason = 'short_stay') AS suppressed_short_stay,
    COUNTIF(reason = 'short_stay' AND open_todos > 0) AS short_stays_with_todos,
    APPROX_QUANTILES(IF(reason = 'short_stay' AND open_todos > 0, stay_min, NULL), 2)[OFFSET(1)]
      AS median_short_stay_min,
    COUNT(DISTINCT user_pseudo_id) AS users_detected,
    COUNT(DISTINCT IF(event_name = 'reminder_notified', user_pseudo_id, NULL)) AS users_reminded
  FROM `__PROJECT__.__DATASET__.events`
  WHERE event_name IN ('reminder_notified', 'reminder_suppressed')
  GROUP BY month, platform, place_event
),
waiting_users AS (
  SELECT DISTINCT DATE_TRUNC(event_date, MONTH) AS month, platform, 'arrival' AS place_event, user_pseudo_id
  FROM `__PROJECT__.__DATASET__.daily_state`
  WHERE places_with_open_todos > 0
  UNION DISTINCT
  SELECT DISTINCT DATE_TRUNC(event_date, MONTH) AS month, platform, 'departure' AS place_event, user_pseudo_id
  FROM `__PROJECT__.__DATASET__.daily_state`
  WHERE open_departure_todos > 0
),
detected_users AS (
  SELECT DISTINCT DATE_TRUNC(event_date, MONTH) AS month, place_event, user_pseudo_id
  FROM `__PROJECT__.__DATASET__.events`
  WHERE event_name IN ('reminder_notified', 'reminder_suppressed')
),
waiting AS (
  SELECT
    w.month,
    w.platform,
    w.place_event,
    COUNT(*) AS users_waiting,
    COUNTIF(d.user_pseudo_id IS NULL) AS users_waiting_without_detection
  FROM waiting_users AS w
  LEFT JOIN detected_users AS d USING (month, place_event, user_pseudo_id)
  GROUP BY w.month, w.platform, w.place_event
)
SELECT
  month,
  platform,
  place_event,
  COALESCE(d.detected, 0) AS detected,
  COALESCE(d.reminders, 0) AS reminders,
  SAFE_DIVIDE(d.reminders, d.detected) AS reminder_rate,
  COALESCE(d.suppressed, 0) AS suppressed,
  COALESCE(d.suppressed_no_open_todos, 0) AS suppressed_no_open_todos,
  COALESCE(d.suppressed_assigned_to_others, 0) AS suppressed_assigned_to_others,
  COALESCE(d.suppressed_recently_notified, 0) AS suppressed_recently_notified,
  COALESCE(d.suppressed_notifications_off, 0) AS suppressed_notifications_off,
  COALESCE(d.suppressed_schedule_failed, 0) AS suppressed_schedule_failed,
  COALESCE(d.suppressed_short_stay, 0) AS suppressed_short_stay,
  COALESCE(d.short_stays_with_todos, 0) AS short_stays_with_todos,
  d.median_short_stay_min,
  COALESCE(d.users_detected, 0) AS users_detected,
  COALESCE(d.users_reminded, 0) AS users_reminded,
  COALESCE(w.users_waiting, 0) AS users_waiting,
  COALESCE(w.users_waiting_without_detection, 0) AS users_waiting_without_detection,
  SAFE_DIVIDE(w.users_waiting_without_detection, w.users_waiting) AS waiting_without_detection_rate
FROM detected AS d
FULL JOIN waiting AS w USING (month, platform, place_event)
