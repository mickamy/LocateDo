-- Apple Watch use by month (iOS only, from 2026-10-09).
-- ios_users: iOS users with a daily_state that month; watch_users: those whose paired Watch had the Watch app on at
-- least one day of it (daily_state carries watch_app_installed from 2026-10-10).
-- watch_completing_users / *_completions: to-dos checked off from the Watch app (via watch) or from the arrival
-- notification on the Watch (via watch_action), as iPhone reports them.
WITH owners AS (
  SELECT
    DATE_TRUNC(event_date, MONTH) AS month,
    COUNT(DISTINCT user_pseudo_id) AS ios_users,
    COUNT(DISTINCT IF(watch_app_installed, user_pseudo_id, NULL)) AS watch_users
  FROM `__PROJECT__.__DATASET__.daily_state`
  WHERE platform = 'IOS'
  GROUP BY month
),
completions AS (
  SELECT
    DATE_TRUNC(event_date, MONTH) AS month,
    COUNT(DISTINCT user_pseudo_id) AS watch_completing_users,
    COUNTIF(via = 'watch') AS watch_app_completions,
    COUNTIF(via = 'watch_action') AS watch_notification_completions
  FROM `__PROJECT__.__DATASET__.events`
  WHERE event_name = 'todo_completed' AND via IN ('watch', 'watch_action')
  GROUP BY month
)
SELECT
  o.month,
  o.ios_users,
  o.watch_users,
  SAFE_DIVIDE(o.watch_users, o.ios_users) AS watch_share,
  COALESCE(c.watch_completing_users, 0) AS watch_completing_users,
  SAFE_DIVIDE(c.watch_completing_users, o.watch_users) AS watch_completing_rate,
  COALESCE(c.watch_app_completions, 0) AS watch_app_completions,
  COALESCE(c.watch_notification_completions, 0) AS watch_notification_completions
FROM owners AS o
LEFT JOIN completions AS c USING (month)
