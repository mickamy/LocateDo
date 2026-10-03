-- name: CreateHousehold :one
INSERT INTO households (id, owner_id)
VALUES ($1, $2)
RETURNING id, owner_id, plan, created_at;

-- name: GetHousehold :one
SELECT id, owner_id, plan, created_at
FROM households
WHERE id = $1;

-- name: DeleteHousehold :execrows
DELETE
FROM households
WHERE id = $1;

-- name: CreateMembership :exec
INSERT INTO memberships (household_id, user_id, role)
VALUES ($1, $2, $3);

-- name: GetMembershipByUser :one
SELECT household_id, user_id, role, joined_at
FROM memberships
WHERE user_id = $1;

-- name: CountMemberships :one
SELECT count(*)
FROM memberships
WHERE household_id = $1;

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
