-- Adding a to-do or a place by where it started, by month and platform: opens of the first
-- screen (todo_editor for to-dos, place_picker for places) and saves with the same entry. A save over the free limit
-- shows the paywall instead and is not counted.
-- kept_preset: to-dos saved at the place the editor started with (the nearest from Home's plus menu, the place it was
-- opened on, or the first place); kept_preset_rate is over the saves that started with one.
WITH opens AS (
  SELECT
    DATE_TRUNC(event_date, MONTH) AS month,
    platform,
    IF(screen = 'todo_editor', 'todo', 'place') AS kind,
    entry,
    COUNT(*) AS opens
  FROM `__PROJECT__.__DATASET__.events`
  WHERE event_name = 'screen_view' AND entry IS NOT NULL AND screen IN ('todo_editor', 'place_picker')
  GROUP BY month, platform, kind, entry
),
saves AS (
  SELECT
    DATE_TRUNC(event_date, MONTH) AS month,
    platform,
    IF(event_name = 'todo_added', 'todo', 'place') AS kind,
    entry,
    COUNT(*) AS saved,
    COUNTIF(place_preset IN ('nearest', 'place', 'first')) AS saved_with_preset,
    COUNTIF(place_preset IN ('nearest', 'place', 'first') AND place_changed = 0) AS kept_preset
  FROM `__PROJECT__.__DATASET__.events`
  WHERE event_name IN ('todo_added', 'place_added') AND entry IS NOT NULL
  GROUP BY month, platform, kind, entry
)
SELECT
  month,
  platform,
  kind,
  entry,
  COALESCE(o.opens, 0) AS opens,
  COALESCE(s.saved, 0) AS saved,
  SAFE_DIVIDE(s.saved, o.opens) AS save_rate,
  COALESCE(s.saved_with_preset, 0) AS saved_with_preset,
  COALESCE(s.kept_preset, 0) AS kept_preset,
  SAFE_DIVIDE(s.kept_preset, s.saved_with_preset) AS kept_preset_rate
FROM opens AS o
FULL JOIN saves AS s USING (month, platform, kind, entry)
