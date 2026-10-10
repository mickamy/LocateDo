-- One row per user per day from the daily_state snapshot (the last one if a day has several).
SELECT * EXCEPT (rank)
FROM (
  SELECT
    event_date,
    user_pseudo_id,
    platform,
    device_brand,
    place_count,
    open_todo_count,
    open_departure_todos,
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
    promotions_consent = 1 AS promotions_consent,
    -- Android only.
    battery_optimization_exempt = 1 AS battery_optimization_exempt,
    -- iOS only: a paired Apple Watch has the Watch app.
    watch_app_installed = 1 AS watch_app_installed,
    language,
    ROW_NUMBER() OVER (PARTITION BY user_pseudo_id, event_date ORDER BY event_time DESC) AS rank
  FROM `__PROJECT__.__DATASET__.events`
  WHERE event_name = 'daily_state'
)
WHERE rank = 1
