-- How screens get opened now that Home is the only root, by month and platform.
-- opens: screen views sent when a screen first appears, which carry where it was opened from (entry). Coming back to a
-- screen, or uncovering it by closing a sheet, sends screen_view again without entry, so those are left out.
-- reach: users who opened the screen from that entry / users active in the month.
WITH active AS (
  SELECT DATE_TRUNC(event_date, MONTH) AS month, platform, COUNT(DISTINCT user_pseudo_id) AS active_users
  FROM `__PROJECT__.__DATASET__.events`
  WHERE foreground
  GROUP BY month, platform
),
opens AS (
  SELECT
    DATE_TRUNC(event_date, MONTH) AS month,
    platform,
    screen,
    entry,
    COUNT(*) AS opens,
    COUNT(DISTINCT user_pseudo_id) AS users
  FROM `__PROJECT__.__DATASET__.events`
  WHERE event_name = 'screen_view' AND entry IS NOT NULL
  GROUP BY month, platform, screen, entry
)
SELECT
  o.month,
  o.platform,
  o.screen,
  o.entry,
  o.opens,
  o.users,
  a.active_users,
  SAFE_DIVIDE(o.users, a.active_users) AS reach
FROM opens AS o
LEFT JOIN active AS a USING (month, platform)
