-- A pending message with the same dedupe key makes this a no-op.
-- name: EnqueueMessage :exec
INSERT INTO outbox_messages (kind, payload, dedupe_key, run_at)
VALUES ($1, $2, $3, $4)
ON CONFLICT (dedupe_key) WHERE status = 'pending' DO NOTHING;

-- name: ClaimMessage :one
SELECT id, kind, payload, dedupe_key, run_at, attempts
FROM outbox_messages
WHERE status = 'pending'
  AND run_at <= $1
ORDER BY run_at
LIMIT 1
    FOR UPDATE SKIP LOCKED;

-- name: CompleteMessage :exec
DELETE
FROM outbox_messages
WHERE id = $1;

-- name: RetryMessage :exec
UPDATE outbox_messages
SET attempts   = attempts + 1,
    run_at     = $2,
    last_error = $3
WHERE id = $1;

-- name: KillMessage :exec
UPDATE outbox_messages
SET attempts   = attempts + 1,
    status     = 'dead',
    last_error = $2
WHERE id = $1;
