-- Whether the Home banner, the Settings buttons, and the reminder setup sheet get permissions fixed, by month and
-- platform.
-- The sheet asks for whatever was missing when it was shown (`missing`), so it counts once per permission it asked for.
-- `missing` lists them with commas (`notifications`, `location_always`, `precise_location`); before 2026-10-10 it was
-- `notifications`, `location_always`, or `both`, and events from before it asked for notifications have none (location).
-- A nudge counts as fixed when, within 24 hours, location reaches "Always", notifications become allowed, or a
-- daily_state reports precise location; daily_state comes about once a day, so precise fixes may be undercounted.
-- users_without_always: users whose daily_state that month showed places but no "Always", the banner's audience.
WITH nudges AS (
  SELECT
    user_pseudo_id,
    platform,
    event_time,
    DATE_TRUNC(event_date, MONTH) AS month,
    CASE
      WHEN event_name = 'permission_banner_tapped' THEN CONCAT('banner_', kind)
      WHEN event_name = 'permission_action_tapped' THEN CONCAT('settings_', kind, '_', action)
      ELSE CONCAT('always_prompt_', result)
    END AS nudge,
    permission
  FROM `__PROJECT__.__DATASET__.events`,
    UNNEST(
      CASE
        WHEN event_name != 'always_prompt_answered' THEN [
          CASE
            WHEN kind = 'precise_location' THEN 'precise_location'
            WHEN STARTS_WITH(kind, 'location') THEN 'location'
            ELSE 'notifications'
          END
        ]
        WHEN missing IS NULL THEN ['location']
        WHEN missing = 'both' THEN ['location', 'notifications']
        ELSE ARRAY(SELECT IF(need = 'location_always', 'location', need) FROM UNNEST(SPLIT(missing, ',')) AS need)
      END
    ) AS permission
  WHERE event_name IN ('permission_banner_tapped', 'permission_action_tapped', 'always_prompt_answered')
),
fixes AS (
  SELECT
    user_pseudo_id,
    event_time,
    CASE event_name
      WHEN 'location_auth_changed' THEN 'location'
      WHEN 'daily_state' THEN 'precise_location'
      ELSE 'notifications'
    END AS permission
  FROM `__PROJECT__.__DATASET__.events`
  WHERE (event_name = 'location_auth_changed' AND auth_to = 'always')
    OR (event_name = 'notification_auth_changed' AND auth_to = 'authorized')
    OR (event_name = 'daily_state' AND precise_location = 1)
),
judged AS (
  SELECT
    n.*,
    EXISTS (
      SELECT 1
      FROM fixes AS f
      WHERE f.user_pseudo_id = n.user_pseudo_id
        AND f.permission = n.permission
        AND f.event_time BETWEEN n.event_time AND TIMESTAMP_ADD(n.event_time, INTERVAL 24 HOUR)
    ) AS fixed
  FROM nudges AS n
),
audience AS (
  SELECT DATE_TRUNC(event_date, MONTH) AS month, platform, COUNT(DISTINCT user_pseudo_id) AS users_without_always
  FROM `__PROJECT__.__DATASET__.daily_state`
  WHERE place_count > 0 AND location_auth != 'always'
  GROUP BY month, platform
)
SELECT
  j.month,
  j.platform,
  j.nudge,
  j.permission,
  COUNT(*) AS nudges,
  COUNT(DISTINCT j.user_pseudo_id) AS users,
  COUNT(DISTINCT IF(j.fixed, j.user_pseudo_id, NULL)) AS fixed_users,
  SAFE_DIVIDE(COUNT(DISTINCT IF(j.fixed, j.user_pseudo_id, NULL)), COUNT(DISTINCT j.user_pseudo_id)) AS fixed_rate,
  a.users_without_always
FROM judged AS j
LEFT JOIN audience AS a USING (month, platform)
GROUP BY j.month, j.platform, j.nudge, j.permission, a.users_without_always
