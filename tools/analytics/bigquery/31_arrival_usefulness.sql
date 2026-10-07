-- Whether arrival reminders get opened and lead to to-dos being checked off, by month.
-- todos_completed_in_app_after_reminders: checked off in the app rather than through the reminder within 60 minutes
-- after the same user got one, e.g., read on the lock screen and opened from the icon. Places are not sent, so these
-- may include to-dos at other places.
WITH in_app_completions AS (
  SELECT
    DATE_TRUNC(c.event_date, MONTH) AS month,
    EXISTS (
      SELECT 1
      FROM `__PROJECT__.__DATASET__.events` AS n
      WHERE n.user_pseudo_id = c.user_pseudo_id
        AND n.event_name = 'arrival_notified'
        AND n.event_time BETWEEN TIMESTAMP_SUB(c.event_time, INTERVAL 60 MINUTE) AND c.event_time
    ) AS after_reminder
  FROM `__PROJECT__.__DATASET__.events` AS c
  WHERE c.event_name = 'todo_completed' AND c.via = 'app'
),
completions AS (
  SELECT month, COUNTIF(after_reminder) AS todos_completed_in_app_after_reminders
  FROM in_app_completions
  GROUP BY month
),
reminders AS (
  SELECT
    DATE_TRUNC(event_date, MONTH) AS month,
    COUNTIF(event_name = 'arrival_notified') AS reminders,
    COUNTIF(event_name = 'arrival_opened') AS reminders_opened,
    SAFE_DIVIDE(COUNTIF(event_name = 'arrival_opened'), COUNTIF(event_name = 'arrival_notified')) AS open_rate,
    APPROX_QUANTILES(IF(event_name = 'arrival_opened', latency_s, NULL), 2)[OFFSET(1)] AS median_open_latency_s,
    COUNTIF(event_name = 'todo_completed') AS todos_completed,
    COUNTIF(event_name = 'todo_completed' AND via = 'notification') AS todos_completed_from_reminders
  FROM `__PROJECT__.__DATASET__.events`
  GROUP BY month
)
SELECT
  r.month,
  r.reminders,
  r.reminders_opened,
  r.open_rate,
  r.median_open_latency_s,
  r.todos_completed,
  r.todos_completed_from_reminders,
  SAFE_DIVIDE(r.todos_completed_from_reminders, r.todos_completed) AS completed_from_reminders_rate,
  COALESCE(c.todos_completed_in_app_after_reminders, 0) AS todos_completed_in_app_after_reminders,
  SAFE_DIVIDE(
    r.todos_completed_from_reminders + COALESCE(c.todos_completed_in_app_after_reminders, 0),
    r.todos_completed
  ) AS completed_after_reminders_rate
FROM reminders AS r
LEFT JOIN completions AS c USING (month)
