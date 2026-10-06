-- Q6: share of users active in the last 30 days who opened each screen at least once.
WITH recent AS (
  SELECT *
  FROM `__PROJECT__.__DATASET__.events`
  WHERE event_date >= DATE_SUB(CURRENT_DATE(), INTERVAL 30 DAY)
),
active AS (
  SELECT COUNT(DISTINCT user_pseudo_id) AS users FROM recent
)
SELECT
  screen,
  COUNT(DISTINCT user_pseudo_id) AS users,
  SAFE_DIVIDE(COUNT(DISTINCT user_pseudo_id), (SELECT users FROM active)) AS reach,
  COUNT(*) AS views
FROM recent
WHERE event_name = 'screen_view' AND screen IS NOT NULL
GROUP BY screen
