-- name: ListHouseholdPushTokens :many
SELECT d.push_token
FROM devices d
         JOIN memberships m ON m.user_id = d.user_id
WHERE m.household_id = $1
  AND d.platform = $2
ORDER BY d.last_seen_at DESC;

-- name: DeleteDeviceByToken :exec
DELETE
FROM devices
WHERE platform = $1
  AND push_token = $2;

-- A token already registered, to anyone, moves to this user.
-- name: UpsertDevice :exec
INSERT INTO devices (user_id, platform, push_token, last_seen_at)
VALUES ($1, $2, $3, $4)
ON CONFLICT (platform, push_token) DO UPDATE
    SET user_id      = EXCLUDED.user_id,
        last_seen_at = EXCLUDED.last_seen_at;
