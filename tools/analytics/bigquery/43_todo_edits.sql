-- How to-dos get edited and deleted, by month and platform.
-- edit_opens / add_opens: the to-do editor opened to edit an existing to-do or to add one.
-- *_deletions: delete actions by where they came from; deleted_todos counts the to-dos they took, since one
-- completed_bulk deletion takes all of a place's or the To-Do tab's completed to-dos.
-- undo_rate: undos over single deletes (swipe, menu, editor); deleting all completed offers no Undo.
WITH edits AS (
  SELECT
    DATE_TRUNC(event_date, MONTH) AS month,
    platform,
    user_pseudo_id,
    event_name,
    screen,
    mode,
    via,
    count
  FROM `__PROJECT__.__DATASET__.events`
  WHERE event_name IN ('todo_deleted', 'todo_delete_undone')
    OR (event_name = 'screen_view' AND screen = 'todo_editor')
)
SELECT
  month,
  platform,
  COUNTIF(event_name = 'screen_view' AND mode = 'add') AS add_opens,
  COUNTIF(event_name = 'screen_view' AND mode = 'edit') AS edit_opens,
  COUNT(DISTINCT IF(event_name = 'screen_view' AND mode = 'edit', user_pseudo_id, NULL)) AS editing_users,
  COUNTIF(event_name = 'todo_deleted' AND via = 'swipe') AS swipe_deletions,
  COUNTIF(event_name = 'todo_deleted' AND via = 'menu') AS menu_deletions,
  COUNTIF(event_name = 'todo_deleted' AND via = 'editor') AS editor_deletions,
  COUNTIF(event_name = 'todo_deleted' AND via = 'completed_bulk') AS completed_bulk_deletions,
  SUM(IF(event_name = 'todo_deleted', count, 0)) AS deleted_todos,
  COUNT(DISTINCT IF(event_name = 'todo_deleted', user_pseudo_id, NULL)) AS deleting_users,
  COUNTIF(event_name = 'todo_delete_undone') AS undos,
  SAFE_DIVIDE(
    COUNTIF(event_name = 'todo_delete_undone'),
    COUNTIF(event_name = 'todo_deleted' AND via IN ('swipe', 'menu', 'editor'))
  ) AS undo_rate
FROM edits
GROUP BY month, platform
