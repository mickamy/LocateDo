-- Whether sharing households stay longer and pay more often.
SELECT
  platform,
  LEAST(COALESCE(max_household_members, 1), 3) AS household_members,
  COUNT(*) AS users,
  SAFE_DIVIDE(COUNTIF(notified_week_2), COUNT(*)) AS notified_week_2_rate,
  SAFE_DIVIDE(COUNTIF(active_week_2), COUNT(*)) AS active_week_2_rate,
  COUNTIF(started_trial) AS trials_started,
  COUNTIF(trial_ended) AS trials_ended,
  SAFE_DIVIDE(COUNTIF(trial_ended AND converted_trial), COUNTIF(trial_ended)) AS trial_conversion_rate,
  SAFE_DIVIDE(COUNTIF(purchased_in_app), COUNT(*)) AS purchase_rate
FROM `__PROJECT__.__DATASET__.users`
WHERE days_since_first >= 14
GROUP BY 1, 2
