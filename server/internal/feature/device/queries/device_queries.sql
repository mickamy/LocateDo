-- A token already registered, to anyone, moves to this user.
-- name: UpsertDevice :exec
INSERT INTO devices (user_id, platform, push_token, last_seen_at)
VALUES ($1, $2, $3, $4)
ON CONFLICT (platform, push_token) DO UPDATE
    SET user_id      = EXCLUDED.user_id,
        last_seen_at = EXCLUDED.last_seen_at;
