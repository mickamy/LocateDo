-- name: CreateHousehold :one
INSERT INTO households (id, owner_id)
VALUES ($1, $2)
RETURNING id, owner_id, plan, version, swept_version, created_at;

-- name: GetHousehold :one
SELECT id, owner_id, plan, version, swept_version, created_at
FROM households
WHERE id = $1;

-- name: GetHouseholdForUpdate :one
SELECT id, owner_id, plan, version, swept_version, created_at
FROM households
WHERE id = $1
FOR UPDATE;

-- name: GetHouseholdByOwner :one
SELECT id, owner_id, plan, version, swept_version, created_at
FROM households
WHERE owner_id = $1;

-- name: ListHouseholdOwnersByPlan :many
SELECT owner_id
FROM households
WHERE plan = $1
ORDER BY owner_id;

-- name: SetHouseholdPlan :execrows
UPDATE households
SET plan = $2
WHERE id = $1
  AND plan <> $2;

-- Both columns take the pre-update version, so every cursor a device holds
-- falls below swept_version and its next Pull resets.
-- name: AdvanceAllHouseholdVersions :many
UPDATE households
SET version       = version + sqlc.arg(step),
    swept_version = version + sqlc.arg(step)
RETURNING id;

-- name: DeleteHousehold :execrows
DELETE
FROM households
WHERE id = $1;

-- name: CreateMembership :exec
INSERT INTO memberships (household_id, user_id, role)
VALUES ($1, $2, $3);

-- name: GetMembershipByUser :one
SELECT m.household_id, m.user_id, m.role, u.display_name, m.joined_at, m.updated_at, m.version
FROM memberships m
         JOIN users u ON u.id = m.user_id
WHERE m.user_id = $1;

-- name: CountMemberships :one
SELECT count(*)
FROM memberships
WHERE household_id = $1;

-- name: ReleaseAssignments :exec
UPDATE todos
SET assignee_id = NULL
WHERE household_id = sqlc.arg(household_id)
  AND assignee_id = sqlc.arg(user_id)::uuid;

-- name: DeleteMembership :execrows
DELETE
FROM memberships
WHERE household_id = $1
  AND user_id = $2;

-- name: CreateInvite :exec
INSERT INTO household_invites (household_id, token_hash, created_by, expires_at)
VALUES ($1, $2, $3, $4);

-- name: AcceptInvite :one
UPDATE household_invites
SET accepted_by = sqlc.arg(accepted_by),
    accepted_at = sqlc.arg(accepted_at)
WHERE token_hash = sqlc.arg(token_hash)
  AND accepted_at IS NULL
RETURNING id, household_id, created_by, expires_at;

-- name: DeferConstraints :exec
SET CONSTRAINTS ALL DEFERRED;

-- name: RemapBuiltinCategories :exec
UPDATE places p
SET category_id = t.id
FROM categories o
         LEFT JOIN categories t
                   ON t.household_id = sqlc.arg(to_household_id)
                       AND t.builtin_key = o.builtin_key
WHERE p.household_id = sqlc.arg(from_household_id)
  AND p.category_id = o.id
  AND o.builtin_key IS NOT NULL;

-- name: MoveCustomCategories :exec
UPDATE categories
SET household_id = sqlc.arg(to_household_id)
WHERE household_id = sqlc.arg(from_household_id)
  AND builtin_key IS NULL;

-- name: MovePlaces :exec
UPDATE places
SET household_id = sqlc.arg(to_household_id)
WHERE household_id = sqlc.arg(from_household_id);

-- name: MoveTodos :exec
UPDATE todos
SET household_id = sqlc.arg(to_household_id)
WHERE household_id = sqlc.arg(from_household_id);

-- name: ImportCategory :exec
INSERT INTO categories (id, household_id, builtin_key, name, icon, color, sort_order)
VALUES ($1, $2, $3, $4, $5, $6, $7);

-- name: ImportPlace :exec
INSERT INTO places (id, household_id, name, lat, lng, radius_m, category_id, sort_order)
VALUES ($1, $2, $3, $4, $5, $6, $7, $8);

-- name: ImportTodo :exec
INSERT INTO todos (id, household_id, place_id, title, assignee_id, completed_at)
VALUES ($1, $2, $3, $4, $5, $6);
