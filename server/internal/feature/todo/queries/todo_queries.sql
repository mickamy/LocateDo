-- name: GetTodo :one
SELECT id, household_id, place_id, title, assignee_id, creator_id, completed_at, updated_at, version
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
INSERT INTO todos (id, household_id, place_id, title, assignee_id, creator_id)
SELECT sqlc.arg(id)::uuid,
       sqlc.arg(household_id)::uuid,
       sqlc.arg(place_id)::uuid,
       sqlc.arg(title)::text,
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
