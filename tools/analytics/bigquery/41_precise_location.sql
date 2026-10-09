-- Whether users without precise location get fewer arrival reminders and stay less, split by location access in their
-- first week. Without precise location the default 100 m radius rarely fires.
SELECT
  platform,
  COALESCE(location_auth_first_week, 'unknown') AS location_auth,
  precise_location_first_week AS precise_location,
  COUNT(*) AS users,
  COUNTIF(notified_week_2) AS notified_week_2_users,
  SAFE_DIVIDE(COUNTIF(notified_week_2), COUNT(*)) AS notified_week_2_rate,
  SAFE_DIVIDE(COUNTIF(active_week_2), COUNT(*)) AS active_week_2_rate,
  COUNTIF(days_since_first >= 37) AS users_old_enough_for_day_30,
  SAFE_DIVIDE(COUNTIF(active_day_30 AND days_since_first >= 37), COUNTIF(days_since_first >= 37)) AS active_day_30_rate
FROM `__PROJECT__.__DATASET__.users`
WHERE days_since_first >= 14
GROUP BY 1, 2, 3
