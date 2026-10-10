-- What happened each day, per platform. User counts are distinct per day, so summing them over days counts a user
-- once for each day they came back.
-- RevenueCat's rc_* events take the platform the user's app reported (users.platform).
-- todos_completed_from_reminders: checked off after opening the reminder or from its checklist (see arrival_usefulness).
SELECT
  e.event_date,
  COALESCE(u.platform, e.platform) AS platform,
  COUNT(DISTINCT IF(foreground, user_pseudo_id, NULL)) AS active_users,
  COUNT(DISTINCT IF(event_name = 'first_open', user_pseudo_id, NULL)) AS new_users,
  COUNTIF(event_name = 'place_added') AS places_added,
  COUNTIF(event_name = 'todo_added') AS todos_added,
  COUNTIF(event_name = 'todo_completed') AS todos_completed,
  COUNTIF(
    event_name = 'todo_completed' AND via IN ('notification', 'action', 'watch_action')
  ) AS todos_completed_from_reminders,
  COUNTIF(event_name = 'arrival_notified') AS reminders,
  COUNTIF(event_name = 'arrival_opened') AS reminders_opened,
  COUNTIF(event_name = 'paywall_shown') AS paywalls_shown,
  COUNTIF(event_name = 'paywall_purchased') AS purchases,
  COUNTIF(event_name = 'rc_trial_started_event') AS trials_started
FROM `__PROJECT__.__DATASET__.events` AS e
LEFT JOIN `__PROJECT__.__DATASET__.users` AS u USING (user_pseudo_id)
GROUP BY e.event_date, platform
