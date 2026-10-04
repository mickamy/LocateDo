-- name: ListHouseholdDevices :many
SELECT d.id, d.user_id, d.platform, d.push_token, d.apns_environment, d.last_seen_at
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

-- name: DeleteUserDeviceByToken :exec
DELETE
FROM devices
WHERE user_id = $1
  AND platform = $2
  AND push_token = $3;

-- A token already registered, to anyone, moves to this user.
-- name: UpsertDevice :exec
INSERT INTO devices (user_id, platform, push_token, apns_environment, last_seen_at)
VALUES ($1, $2, $3, $4, $5)
ON CONFLICT (platform, push_token) DO UPDATE
    SET user_id          = EXCLUDED.user_id,
        apns_environment = EXCLUDED.apns_environment,
        last_seen_at     = EXCLUDED.last_seen_at;
