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

-- name: ListTodoChanges :many
SELECT id, household_id, place_id, title, assignee_id, completed_at, updated_at, version
FROM todos
WHERE household_id = $1
  AND version > $2
ORDER BY version
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
