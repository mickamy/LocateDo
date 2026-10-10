-- Whether reminders get opened and lead to to-dos being checked off, by month and platform; the arrival_ /
-- departure_ columns split them by kind.
-- todos_completed_from_reminders: checked off within 30 minutes of opening the reminder (via notification) or from
-- its checklist without opening the app, on iPhone (via action) or Apple Watch (via watch_action); both are also
-- counted in todos_completed_from_actions, and the Watch ones in todos_completed_from_watch_actions.
-- todos_completed_in_app_after_reminders: checked off in the app (iPhone or Watch) rather than through the reminder
-- within 60 minutes after the same user got one, e.g., read on the lock screen and opened from the icon. Places are
-- not sent, so these may include to-dos at other places.
WITH in_app_completions AS (
  SELECT
    DATE_TRUNC(c.event_date, MONTH) AS month,
    c.platform,
    EXISTS (
      SELECT 1
      FROM `__PROJECT__.__DATASET__.events` AS n
      WHERE n.user_pseudo_id = c.user_pseudo_id
        AND n.event_name = 'reminder_notified'
        AND n.event_time BETWEEN TIMESTAMP_SUB(c.event_time, INTERVAL 60 MINUTE) AND c.event_time
    ) AS after_reminder
  FROM `__PROJECT__.__DATASET__.events` AS c
  WHERE c.event_name = 'todo_completed' AND c.via IN ('app', 'watch')
),
completions AS (
  SELECT month, platform, COUNTIF(after_reminder) AS todos_completed_in_app_after_reminders
  FROM in_app_completions
  GROUP BY month, platform
),
reminders AS (
  SELECT
    DATE_TRUNC(event_date, MONTH) AS month,
    platform,
    COUNTIF(event_name = 'reminder_notified') AS reminders,
    COUNTIF(event_name = 'reminder_notified' AND place_event = 'arrival') AS arrival_reminders,
    COUNTIF(event_name = 'reminder_notified' AND place_event = 'departure') AS departure_reminders,
    COUNTIF(event_name = 'reminder_opened') AS reminders_opened,
    COUNTIF(event_name = 'reminder_opened' AND place_event = 'arrival') AS arrival_reminders_opened,
    COUNTIF(event_name = 'reminder_opened' AND place_event = 'departure') AS departure_reminders_opened,
    APPROX_QUANTILES(IF(event_name = 'reminder_opened', latency_s, NULL), 2)[OFFSET(1)] AS median_open_latency_s,
    COUNTIF(event_name = 'todo_completed') AS todos_completed,
    COUNTIF(
      event_name = 'todo_completed' AND via IN ('notification', 'action', 'watch_action')
    ) AS todos_completed_from_reminders,
    COUNTIF(
      event_name = 'todo_completed' AND via IN ('notification', 'action', 'watch_action') AND place_event = 'arrival'
    ) AS todos_completed_from_arrival_reminders,
    COUNTIF(
      event_name = 'todo_completed' AND via IN ('notification', 'action', 'watch_action') AND place_event = 'departure'
    ) AS todos_completed_from_departure_reminders,
    COUNTIF(event_name = 'todo_completed' AND via IN ('action', 'watch_action')) AS todos_completed_from_actions,
    COUNTIF(event_name = 'todo_completed' AND via = 'watch_action') AS todos_completed_from_watch_actions
  FROM `__PROJECT__.__DATASET__.events`
  GROUP BY month, platform
)
SELECT
  r.month,
  r.platform,
  r.reminders,
  r.arrival_reminders,
  r.departure_reminders,
  r.reminders_opened,
  r.arrival_reminders_opened,
  r.departure_reminders_opened,
  SAFE_DIVIDE(r.reminders_opened, r.reminders) AS open_rate,
  SAFE_DIVIDE(r.arrival_reminders_opened, r.arrival_reminders) AS arrival_open_rate,
  SAFE_DIVIDE(r.departure_reminders_opened, r.departure_reminders) AS departure_open_rate,
  r.median_open_latency_s,
  r.todos_completed,
  r.todos_completed_from_reminders,
  r.todos_completed_from_arrival_reminders,
  r.todos_completed_from_departure_reminders,
  r.todos_completed_from_actions,
  r.todos_completed_from_watch_actions,
  SAFE_DIVIDE(r.todos_completed_from_reminders, r.todos_completed) AS completed_from_reminders_rate,
  COALESCE(c.todos_completed_in_app_after_reminders, 0) AS todos_completed_in_app_after_reminders,
  SAFE_DIVIDE(
    r.todos_completed_from_reminders + COALESCE(c.todos_completed_in_app_after_reminders, 0),
    r.todos_completed
  ) AS completed_after_reminders_rate
FROM reminders AS r
LEFT JOIN completions AS c USING (month, platform)
