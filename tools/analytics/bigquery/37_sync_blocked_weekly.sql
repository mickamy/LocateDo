-- Writes held back because the device was Pro while the server plan was still free, by ISO week (Monday start).
-- A block is resolved when the same user's next sync event is sync_unblocked; durations come from that event.
WITH sync AS (
  SELECT
    user_pseudo_id,
    event_date,
    event_time,
    event_name,
    kind,
    result,
    duration_s,
    LEAD(event_name) OVER (PARTITION BY user_pseudo_id ORDER BY event_time) AS next_event
  FROM `__PROJECT__.__DATASET__.events`
  WHERE event_name IN ('sync_blocked_by_plan', 'sync_unblocked')
),
active AS (
  SELECT DATE_TRUNC(event_date, WEEK(MONDAY)) AS week, COUNT(DISTINCT user_pseudo_id) AS active_users
  FROM `__PROJECT__.__DATASET__.events`
  GROUP BY week
),
blocked AS (
  SELECT
    DATE_TRUNC(event_date, WEEK(MONDAY)) AS week,
    COUNT(*) AS blocks,
    COUNT(DISTINCT user_pseudo_id) AS blocked_users,
    COUNTIF(kind = 'place') AS place_blocks,
    COUNTIF(kind = 'todo') AS todo_blocks,
    COUNTIF(next_event IS NULL OR next_event != 'sync_unblocked') AS unresolved_blocks
  FROM sync
  WHERE event_name = 'sync_blocked_by_plan'
  GROUP BY week
),
unblocked AS (
  SELECT
    DATE_TRUNC(event_date, WEEK(MONDAY)) AS week,
    COUNT(*) AS unblocks,
    COUNTIF(result = 'sent') AS unblocked_sent,
    COUNTIF(result = 'rejected') AS unblocked_rejected,
    COUNTIF(result = 'cleared') AS unblocked_cleared,
    APPROX_QUANTILES(duration_s, 2)[SAFE_OFFSET(1)] / 3600 AS median_blocked_hours,
    MAX(duration_s) / 3600 AS max_blocked_hours
  FROM sync
  WHERE event_name = 'sync_unblocked'
  GROUP BY week
)
SELECT
  a.week,
  a.active_users,
  COALESCE(b.blocked_users, 0) AS blocked_users,
  SAFE_DIVIDE(COALESCE(b.blocked_users, 0), a.active_users) AS blocked_user_rate,
  COALESCE(b.blocks, 0) AS blocks,
  COALESCE(b.place_blocks, 0) AS place_blocks,
  COALESCE(b.todo_blocks, 0) AS todo_blocks,
  COALESCE(b.unresolved_blocks, 0) AS unresolved_blocks,
  COALESCE(u.unblocks, 0) AS unblocks,
  COALESCE(u.unblocked_sent, 0) AS unblocked_sent,
  COALESCE(u.unblocked_rejected, 0) AS unblocked_rejected,
  COALESCE(u.unblocked_cleared, 0) AS unblocked_cleared,
  u.median_blocked_hours,
  u.max_blocked_hours
FROM active AS a
LEFT JOIN blocked AS b USING (week)
LEFT JOIN unblocked AS u USING (week)
