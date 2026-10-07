-- name: CountAudience :many
SELECT platform, count(*) AS devices
FROM devices
WHERE language = $1
  AND promotions_consented_at IS NOT NULL
GROUP BY platform;

-- name: FindLastSentAt :one
SELECT sent_at
FROM campaigns
WHERE language = $1
  AND sent_at IS NOT NULL
ORDER BY sent_at DESC
LIMIT 1;

-- name: CountUnsent :one
SELECT count(*)
FROM campaigns
WHERE language = $1
  AND sent_at IS NULL;

-- name: InsertCampaign :one
INSERT INTO campaigns (language, title, body, url, target_count, created_at)
VALUES ($1, $2, $3, $4, $5, $6)
RETURNING id;
