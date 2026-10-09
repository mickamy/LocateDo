-- Share of users active in the last 30 days who opened each screen at least once, per platform.
WITH recent AS (
  SELECT *
  FROM `__PROJECT__.__DATASET__.events`
  WHERE event_date >= DATE_SUB(CURRENT_DATE(), INTERVAL 30 DAY)
),
active AS (
  SELECT platform, COUNT(DISTINCT user_pseudo_id) AS users FROM recent WHERE foreground GROUP BY platform
)
SELECT
  r.platform,
  r.screen,
  COUNT(DISTINCT r.user_pseudo_id) AS users,
  SAFE_DIVIDE(COUNT(DISTINCT r.user_pseudo_id), ANY_VALUE(a.users)) AS reach,
  COUNT(*) AS views
FROM recent AS r
LEFT JOIN active AS a USING (platform)
WHERE r.event_name = 'screen_view' AND r.screen IS NOT NULL
GROUP BY r.platform, r.screen
