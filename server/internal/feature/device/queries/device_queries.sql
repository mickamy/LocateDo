-- name: ListHouseholdDevices :many
SELECT d.id, d.user_id, d.platform, d.push_token, d.apns_environment, d.language, d.promotions_consented_at, d.last_seen_at
FROM devices d
         JOIN memberships m ON m.user_id = d.user_id
WHERE m.household_id = $1
  AND d.platform = $2
ORDER BY d.last_seen_at DESC;

-- name: FindDeviceByTokenForUpdate :one
SELECT id, user_id, platform, push_token, apns_environment, language, promotions_consented_at, last_seen_at
FROM devices
WHERE platform = $1
  AND push_token = $2
    FOR UPDATE;

-- name: DeleteDevice :exec
DELETE
FROM devices
WHERE id = $1;

-- name: DeleteDeviceByToken :exec
DELETE
FROM devices
WHERE platform = $1
  AND push_token = $2;

-- name: DetachConsentingUserDeviceByToken :exec
UPDATE devices
SET user_id = NULL
WHERE user_id = $1
  AND platform = $2
  AND push_token = $3
  AND promotions_consented_at IS NOT NULL;

-- name: DeleteUserDeviceByToken :exec
DELETE
FROM devices
WHERE user_id = $1
  AND platform = $2
  AND push_token = $3
  AND promotions_consented_at IS NULL;

-- name: DeleteAnonymousDevicesUnseenSince :execrows
DELETE
FROM devices
WHERE user_id IS NULL
  AND last_seen_at < $1;

-- A token already registered moves to this user; an anonymous registration
-- (no user) leaves its owner in place. Consenting again keeps the original time.
-- name: UpsertDevice :one
INSERT INTO devices (user_id, platform, push_token, apns_environment, language, promotions_consented_at, last_seen_at)
VALUES ($1, $2, $3, $4, $5, $6, $7)
ON CONFLICT (platform, push_token) DO UPDATE
    SET user_id                 = COALESCE(EXCLUDED.user_id, devices.user_id),
        apns_environment        = EXCLUDED.apns_environment,
        language                = EXCLUDED.language,
        promotions_consented_at = CASE
                                      WHEN EXCLUDED.promotions_consented_at IS NOT NULL
                                          THEN COALESCE(devices.promotions_consented_at,
                                                        EXCLUDED.promotions_consented_at)
            END,
        last_seen_at            = EXCLUDED.last_seen_at
RETURNING id;

-- name: InsertPromotionsConsentChange :exec
INSERT INTO promotions_consent_changes (device_id, user_id, consented, changed_at)
VALUES ($1, $2, $3, $4);
