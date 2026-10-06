-- How much people set up in their first three days, against whether they are still around later.
SELECT
  LEAST(places_added_first_3_days, 4) AS places_added_first_3_days,
  LEAST(todos_added_first_3_days, 10) AS todos_added_first_3_days,
  COUNT(*) AS users,
  COUNTIF(active_week_2) AS active_week_2_users,
  SAFE_DIVIDE(COUNTIF(active_week_2), COUNT(*)) AS active_week_2_rate,
  COUNTIF(notified_week_2) AS notified_week_2_users,
  SAFE_DIVIDE(COUNTIF(notified_week_2), COUNT(*)) AS notified_week_2_rate,
  COUNTIF(days_since_first >= 37) AS users_old_enough_for_day_30,
  SAFE_DIVIDE(COUNTIF(active_day_30 AND days_since_first >= 37), COUNTIF(days_since_first >= 37)) AS active_day_30_rate
FROM `__PROJECT__.__DATASET__.users`
WHERE days_since_first >= 14
GROUP BY 1, 2
