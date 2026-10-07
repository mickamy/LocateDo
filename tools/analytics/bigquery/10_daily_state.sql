-- One row per user per day from the daily_state snapshot (the last one if a day has several).
SELECT * EXCEPT (rank)
FROM (
  SELECT
    event_date,
    user_pseudo_id,
    place_count,
    open_todo_count,
    completed_todo_count_7d,
    places_with_open_todos,
    custom_category_count,
    household_members,
    days_since_install,
    plan,
    signed_in = 1 AS signed_in,
    location_auth,
    precise_location = 1 AS precise_location,
    notification_auth,
    marketing_consent = 1 AS marketing_consent,
    language,
    ROW_NUMBER() OVER (PARTITION BY user_pseudo_id, event_date ORDER BY event_time DESC) AS rank
  FROM `__PROJECT__.__DATASET__.events`
  WHERE event_name = 'daily_state'
)
WHERE rank = 1
