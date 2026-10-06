-- Q3: where "Always" location access is lost: the onboarding choice, and later changes.
SELECT
  DATE_TRUNC(event_date, MONTH) AS month,
  IF(event_name = 'onboarding_completed', 'onboarding', 'changed') AS moment,
  IF(event_name = 'onboarding_completed', CAST(NULL AS STRING), auth_from) AS location_from,
  IF(event_name = 'onboarding_completed', location_auth, auth_to) AS location_to,
  IF(event_name = 'onboarding_completed', notification_auth, CAST(NULL AS STRING)) AS notifications,
  COUNT(DISTINCT user_pseudo_id) AS users,
  APPROX_QUANTILES(IF(event_name = 'onboarding_completed', duration_s, NULL), 2)[OFFSET(1)] AS median_onboarding_s
FROM `__PROJECT__.__DATASET__.events`
WHERE event_name IN ('onboarding_completed', 'location_auth_changed')
GROUP BY month, moment, location_from, location_to, notifications
