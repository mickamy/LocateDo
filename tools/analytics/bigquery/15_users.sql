-- One row per user: when they started and what they did in their first days and weeks.
-- first_open marks a new install; users who installed before the export started fall back to their first exported day.
WITH firsts AS (
  SELECT
    user_pseudo_id,
    COALESCE(MIN(IF(event_name = 'first_open', event_date, NULL)), MIN(event_date)) AS first_date
  FROM `__PROJECT__.__DATASET__.events`
  GROUP BY user_pseudo_id
)
SELECT
  f.user_pseudo_id,
  f.first_date,
  COUNTIF(e.event_name = 'place_added' AND DATE_DIFF(e.event_date, f.first_date, DAY) < 3) AS places_added_first_3_days,
  COUNTIF(e.event_name = 'todo_added' AND DATE_DIFF(e.event_date, f.first_date, DAY) < 3) AS todos_added_first_3_days,
  LOGICAL_OR(DATE_DIFF(e.event_date, f.first_date, DAY) BETWEEN 7 AND 13) AS active_week_2,
  LOGICAL_OR(e.event_name = 'arrival_notified' AND DATE_DIFF(e.event_date, f.first_date, DAY) BETWEEN 7 AND 13) AS notified_week_2,
  LOGICAL_OR(DATE_DIFF(e.event_date, f.first_date, DAY) BETWEEN 30 AND 36) AS active_day_30,
  MAX(e.household_members) AS max_household_members,
  LOGICAL_OR(e.event_name = 'share_tapped') AS tapped_share,
  LOGICAL_OR(e.event_name = 'limit_reached') AS reached_limit,
  LOGICAL_OR(e.event_name = 'paywall_purchased') AS purchased_in_app,
  LOGICAL_OR(e.event_name = 'rc_trial_started_event') AS started_trial,
  LOGICAL_OR(e.event_name = 'rc_trial_converted_event') AS converted_trial,
  DATE_DIFF(CURRENT_DATE(), f.first_date, DAY) AS days_since_first
FROM firsts AS f
JOIN `__PROJECT__.__DATASET__.events` AS e USING (user_pseudo_id)
GROUP BY f.user_pseudo_id, f.first_date
