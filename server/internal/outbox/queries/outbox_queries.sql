-- A pending message with the same dedupe key makes this a no-op.
-- name: EnqueueMessage :exec
INSERT INTO outbox_messages (kind, payload, dedupe_key, run_at)
VALUES ($1, $2, $3, $4)
ON CONFLICT (dedupe_key) WHERE status = 'pending' DO NOTHING;

-- Takes the oldest due message, or one whose lease ran out because its
-- worker died, and leases it until lease_until.
-- name: ClaimMessage :one
UPDATE outbox_messages
SET status      = 'running',
    lease_until = sqlc.arg(lease_until)
WHERE id = (SELECT due.id
            FROM outbox_messages due
            WHERE (due.status = 'pending' AND due.run_at <= sqlc.arg(now))
               OR (due.status = 'running' AND due.lease_until <= sqlc.arg(now))
            ORDER BY due.run_at
            LIMIT 1 FOR UPDATE SKIP LOCKED)
RETURNING id, kind, payload, dedupe_key, run_at, attempts, created_at;

-- name: CompleteMessage :exec
DELETE
FROM outbox_messages
WHERE id = $1;

-- Skipped when a pending message with the same dedupe key already waits.
-- name: RetryMessage :execrows
UPDATE outbox_messages m
SET status      = 'pending',
    lease_until = NULL,
    attempts    = m.attempts + 1,
    run_at      = $2,
    last_error  = $3
WHERE m.id = $1
  AND NOT EXISTS (SELECT 1
                  FROM outbox_messages p
                  WHERE p.status = 'pending'
                    AND p.dedupe_key = m.dedupe_key);

-- name: SweepDeadMessages :execrows
DELETE
FROM outbox_messages
WHERE status = 'dead'
  AND created_at < $1;

-- name: KillMessage :exec
UPDATE outbox_messages
SET attempts    = attempts + 1,
    status      = 'dead',
    lease_until = NULL,
    last_error  = $2
WHERE id = $1;
