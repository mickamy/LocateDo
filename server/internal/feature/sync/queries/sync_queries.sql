-- name: ListMembershipChanges :many
SELECT m.household_id, m.user_id, m.role, u.display_name, m.joined_at, m.updated_at, m.version
FROM memberships m
         JOIN users u ON u.id = m.user_id
WHERE m.household_id = $1
  AND m.version > $2
ORDER BY m.version
LIMIT $3;

-- name: ListCategoryChanges :many
SELECT id, household_id, builtin_key, name, icon, color, sort_order, updated_at, version
FROM categories
WHERE household_id = $1
  AND version > $2
ORDER BY version
LIMIT $3;

-- name: ListPlaceChanges :many
SELECT id, household_id, name, lat, lng, radius_m, category_id, sort_order, updated_at, version
FROM places
WHERE household_id = $1
  AND version > $2
ORDER BY version
LIMIT $3;

-- The completer is the one of the latest completion not reopened.
-- name: ListTodoChanges :many
SELECT t.id,
       t.household_id,
       t.place_id,
       t.title,
       t.notify_on,
       t.assignee_id,
       t.creator_id,
       c.completer_id,
       t.completed_at,
       t.updated_at,
       t.version
FROM todos t
         LEFT JOIN LATERAL (SELECT tc.completer_id
                            FROM todo_completions tc
                            WHERE tc.todo_id = t.id
                              AND tc.reopened_at IS NULL
                            ORDER BY tc.id DESC
                            LIMIT 1) c ON true
WHERE t.household_id = $1
  AND t.version > $2
ORDER BY t.version
LIMIT $3;

-- name: SweepDeletions :many
DELETE
FROM deletions
WHERE deleted_at < $1
RETURNING household_id, version;

-- name: RaiseSweptVersion :exec
UPDATE households
SET swept_version = GREATEST(swept_version, sqlc.arg(version))
WHERE id = sqlc.arg(id);

-- name: ListDeletions :many
SELECT table_name, row_id, version
FROM deletions
WHERE household_id = $1
  AND version > $2
ORDER BY version
LIMIT $3;
