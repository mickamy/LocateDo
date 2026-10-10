-- Which row of Home's list of places (nearest first) gets opened, by month and platform.
-- Rows past the tenth are counted as 10.
SELECT
  DATE_TRUNC(event_date, MONTH) AS month,
  platform,
  LEAST(rank, 10) AS list_rank,
  COUNT(*) AS opens,
  COUNT(DISTINCT user_pseudo_id) AS users
FROM `__PROJECT__.__DATASET__.events`
WHERE event_name = 'screen_view' AND screen = 'place_detail' AND entry = 'home_list' AND rank IS NOT NULL
GROUP BY 1, 2, 3
