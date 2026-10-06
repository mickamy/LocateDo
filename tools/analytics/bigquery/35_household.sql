-- Whether sharing households stay longer and pay more often.
-- Split by blocked_by_plan: those owners paid but could not invite until the server caught up, so they undercount sharing.
SELECT
  LEAST(COALESCE(max_household_members, 1), 3) AS household_members,
  COALESCE(blocked_by_plan, FALSE) AS blocked_by_plan,
  COUNT(*) AS users,
  SAFE_DIVIDE(COUNTIF(notified_week_2), COUNT(*)) AS notified_week_2_rate,
  SAFE_DIVIDE(COUNTIF(active_week_2), COUNT(*)) AS active_week_2_rate,
  COUNTIF(started_trial) AS trials_started,
  SAFE_DIVIDE(COUNTIF(converted_trial), COUNTIF(started_trial)) AS trial_conversion_rate,
  SAFE_DIVIDE(COUNTIF(purchased_in_app), COUNT(*)) AS purchase_rate
FROM `__PROJECT__.__DATASET__.users`
WHERE days_since_first >= 14
GROUP BY 1, 2
