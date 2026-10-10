-- Adding a place, by month and platform (from 2026-10-10, when it became map, details, category, to-dos).
-- *_views: how far new places get; map_views also counts moving a saved place's pin, the others are new places only.
-- with_todos_rate: places added together with at least one to-do. guessed / guess_kept: a category was guessed from the
-- picked store, and the place was saved with that category.
-- duplicate_*: the pin landed within 50 m of a saved place, and what was chosen.
-- map_tab_*: Map tab views by where they came from.
WITH adding AS (
  SELECT
    DATE_TRUNC(event_date, MONTH) AS month,
    platform,
    event_name,
    screen,
    mode,
    step,
    source,
    category,
    suggested_category,
    todo_count,
    choice
  FROM `__PROJECT__.__DATASET__.events`
  WHERE event_name IN ('place_added', 'place_duplicate_prompted')
    OR (event_name = 'screen_view' AND screen IN ('place_picker', 'place_editor', 'map'))
)
SELECT
  month,
  platform,
  COUNTIF(event_name = 'screen_view' AND screen = 'place_picker') AS map_views,
  COUNTIF(event_name = 'screen_view' AND screen = 'place_editor' AND step = 'details') AS details_views,
  COUNTIF(event_name = 'screen_view' AND screen = 'place_editor' AND step = 'category') AS category_views,
  COUNTIF(event_name = 'screen_view' AND screen = 'place_editor' AND step = 'todos') AS todos_views,
  COUNTIF(event_name = 'place_added') AS places_added,
  COUNTIF(event_name = 'place_added' AND todo_count > 0) AS added_with_todos,
  SAFE_DIVIDE(
    COUNTIF(event_name = 'place_added' AND todo_count > 0),
    COUNTIF(event_name = 'place_added' AND todo_count IS NOT NULL)
  ) AS with_todos_rate,
  SUM(IF(event_name = 'place_added', todo_count, 0)) AS todos_with_places,
  COUNTIF(event_name = 'place_added' AND suggested_category IN ('shopping', 'life')) AS guessed,
  COUNTIF(event_name = 'place_added' AND suggested_category IN ('shopping', 'life') AND category = suggested_category) AS guess_kept,
  SAFE_DIVIDE(
    COUNTIF(event_name = 'place_added' AND suggested_category IN ('shopping', 'life') AND category = suggested_category),
    COUNTIF(event_name = 'place_added' AND suggested_category IN ('shopping', 'life'))
  ) AS guess_kept_rate,
  COUNTIF(event_name = 'place_duplicate_prompted') AS duplicate_prompts,
  COUNTIF(event_name = 'place_duplicate_prompted' AND choice = 'open') AS duplicate_opened,
  COUNTIF(event_name = 'place_duplicate_prompted' AND choice = 'add') AS duplicate_added,
  COUNTIF(event_name = 'place_duplicate_prompted' AND choice = 'cancel') AS duplicate_canceled,
  COUNTIF(event_name = 'screen_view' AND screen = 'map' AND source = 'tab') AS map_tab_from_tab_bar,
  COUNTIF(event_name = 'screen_view' AND screen = 'map' AND source = 'home_preview') AS map_tab_from_home_preview
FROM adding
GROUP BY month, platform
