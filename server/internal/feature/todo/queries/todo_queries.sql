-- name: GetTodo :one
SELECT id, household_id, place_id, title, notify_on, assignee_id, creator_id, completed_at, updated_at, version
FROM todos
WHERE id = $1
  AND household_id = $2;

-- name: CountOpenTodos :one
SELECT count(*)
FROM todos
WHERE household_id = $1
  AND completed_at IS NULL;

-- Writes nothing when the place is not in the household. An assignee who is
-- not a member resolves to NULL. completed_at and creator_id are left alone on
-- update.
-- name: UpsertTodo :execrows
INSERT INTO todos (id, household_id, place_id, title, notify_on, assignee_id, creator_id)
SELECT sqlc.arg(id)::uuid,
       sqlc.arg(household_id)::uuid,
       sqlc.arg(place_id)::uuid,
       sqlc.arg(title)::text,
       sqlc.arg(notify_on)::text,
       (SELECT m.user_id
        FROM memberships m
        WHERE m.user_id = sqlc.narg(assignee_id)::uuid
          AND m.household_id = sqlc.arg(household_id)::uuid),
       sqlc.narg(creator_id)::uuid
WHERE EXISTS (SELECT 1
              FROM places p
              WHERE p.id = sqlc.arg(place_id)::uuid
                AND p.household_id = sqlc.arg(household_id)::uuid)
ON CONFLICT (id, household_id) DO UPDATE
    SET place_id    = EXCLUDED.place_id,
        title       = EXCLUDED.title,
        notify_on   = EXCLUDED.notify_on,
        assignee_id = EXCLUDED.assignee_id;

-- name: SetTodoCompletion :exec
UPDATE todos
SET completed_at = $3
WHERE id = $1
  AND household_id = $2;

-- name: DeleteTodo :exec
DELETE
FROM todos
WHERE id = $1
  AND household_id = $2;

-- name: InsertCompletion :exec
INSERT INTO todo_completions (todo_id, completer_id, completed_at)
VALUES ($1, $2, $3);

-- name: ReopenCompletions :exec
UPDATE todo_completions
SET reopened_at = $2
WHERE todo_id = $1
  AND reopened_at IS NULL;

-- Marks the creator's to-dos the completer checked off and nobody announced
-- yet, skipping reopened ones and to-dos announced before, and returns them.
-- name: ClaimCompletionNotice :many
UPDATE todo_completions c
SET notified_at = sqlc.arg(notified_at)
FROM todos t
WHERE t.id = c.todo_id
  AND t.household_id = sqlc.arg(household_id)
  AND t.creator_id = sqlc.arg(creator_id)
  AND t.completed_at IS NOT NULL
  AND c.completer_id = sqlc.arg(completer_id)
  AND c.reopened_at IS NULL
  AND c.notified_at IS NULL
  AND NOT EXISTS (SELECT 1
                  FROM todo_completions p
                  WHERE p.todo_id = c.todo_id
                    AND p.notified_at IS NOT NULL)
RETURNING t.title, c.completed_at;
