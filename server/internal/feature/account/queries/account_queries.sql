-- name: GetUserByIdentity :one
SELECT u.id, u.display_name, u.created_at
FROM users u
         JOIN user_identities i ON i.user_id = u.id
WHERE i.provider = $1
  AND i.subject = $2;

-- name: CreateUser :one
INSERT INTO users (display_name)
VALUES ($1)
RETURNING id, display_name, created_at;

-- name: CreateUserIdentity :exec
INSERT INTO user_identities (user_id, provider, subject)
VALUES ($1, $2, $3);

-- name: DeleteUser :execrows
DELETE
FROM users
WHERE id = $1;

-- name: CreateRefreshToken :exec
INSERT INTO refresh_tokens (user_id, family_id, token_hash, expires_at)
VALUES ($1, $2, $3, $4);

-- name: UseRefreshToken :one
UPDATE refresh_tokens
SET used_at = sqlc.arg(used_at)
WHERE token_hash = sqlc.arg(token_hash)
  AND used_at IS NULL
RETURNING id, user_id, family_id, expires_at, used_at;

-- name: GetRefreshTokenByHash :one
SELECT id, user_id, family_id, expires_at, used_at
FROM refresh_tokens
WHERE token_hash = $1;

-- name: DeleteRefreshTokenFamily :exec
DELETE
FROM refresh_tokens
WHERE family_id = $1;

-- name: DeleteExpiredRefreshTokens :execrows
DELETE
FROM refresh_tokens
WHERE expires_at < $1;

-- name: UpsertAppleToken :exec
INSERT INTO apple_tokens (user_id, refresh_token_ciphertext, client)
VALUES ($1, $2, $3)
ON CONFLICT (user_id) DO UPDATE
    SET refresh_token_ciphertext = excluded.refresh_token_ciphertext,
        client                   = excluded.client,
        updated_at               = now();

-- name: GetAppleToken :one
SELECT refresh_token_ciphertext, client
FROM apple_tokens
WHERE user_id = $1;
