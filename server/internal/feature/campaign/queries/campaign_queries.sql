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

-- name: FindCampaign :one
SELECT id, language, title, body, url, target_count, created_at, sent_at
FROM campaigns
WHERE id = $1;

-- Devices consenting in the campaign's language that it has not reached, after
-- the given device id.
-- name: ListRecipients :many
SELECT d.id, d.platform, d.push_token, d.apns_environment
FROM devices d
WHERE d.language = @language
  AND d.promotions_consented_at IS NOT NULL
  AND d.id > @after
  AND NOT EXISTS (SELECT 1
                  FROM campaign_deliveries cd
                  WHERE cd.campaign_id = @campaign_id
                    AND cd.device_id = d.id)
ORDER BY d.id
LIMIT @page_size;

-- name: ClaimDelivery :execrows
INSERT INTO campaign_deliveries (campaign_id, device_id, attempted_at)
VALUES ($1, $2, $3)
ON CONFLICT DO NOTHING;

-- name: MarkDelivered :exec
UPDATE campaign_deliveries
SET sent_at = $3
WHERE campaign_id = $1
  AND device_id = $2;

-- name: ReleaseDelivery :exec
DELETE
FROM campaign_deliveries
WHERE campaign_id = $1
  AND device_id = $2;

-- name: AddUnregistered :exec
UPDATE campaigns
SET unregistered_count = COALESCE(unregistered_count, 0) + 1
WHERE id = $1;

-- name: FinishCampaign :exec
UPDATE campaigns
SET sent_at            = @sent_at::timestamptz,
    sent_count         = (SELECT count(*)
                          FROM campaign_deliveries cd
                          WHERE cd.campaign_id = @id
                            AND cd.sent_at IS NOT NULL),
    unregistered_count = COALESCE(unregistered_count, 0),
    failed_count       = @failed_count::integer,
    uncertain_count    = (SELECT count(*)
                          FROM campaign_deliveries cd
                          WHERE cd.campaign_id = @id
                            AND cd.sent_at IS NULL)
WHERE id = @id
  AND sent_at IS NULL;
