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
  AND dead_at < sqlc.arg(before)::timestamptz;

-- name: KillMessage :exec
UPDATE outbox_messages
SET attempts    = attempts + 1,
    status      = 'dead',
    lease_until = NULL,
    dead_at     = sqlc.arg(dead_at)::timestamptz,
    last_error  = sqlc.arg(last_error)
WHERE id = sqlc.arg(id);

-- Overdue: due pending messages and running ones whose lease ran out, measured
-- from when they should have been taken. Oldest: anything not yet delivered.
-- name: GetOutboxHealth :one
SELECT coalesce(extract(EPOCH FROM sqlc.arg(now)::timestamptz - min(CASE
    WHEN status = 'pending' AND run_at <= sqlc.arg(now) THEN run_at
    WHEN status = 'running' AND lease_until <= sqlc.arg(now) THEN lease_until
    END)), 0)::bigint                                                            AS overdue_seconds,
       coalesce(extract(EPOCH FROM sqlc.arg(now)::timestamptz - min(created_at)
                                    FILTER (WHERE status IN ('pending', 'running'))), 0)::bigint AS oldest_seconds,
       count(*) FILTER (WHERE status = 'dead' AND dead_at > sqlc.arg(dead_since)::timestamptz)::bigint       AS dead
FROM outbox_messages;
