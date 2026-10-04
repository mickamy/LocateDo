-- name: UpsertCategory :exec
INSERT INTO categories (id, household_id, builtin_key, name, icon, color, sort_order)
VALUES ($1, $2, $3, $4, $5, $6, $7)
ON CONFLICT (id, household_id) DO UPDATE
    SET builtin_key = EXCLUDED.builtin_key,
        name        = EXCLUDED.name,
        icon        = EXCLUDED.icon,
        color       = EXCLUDED.color,
        sort_order  = EXCLUDED.sort_order;

-- name: DeleteCategory :exec
DELETE
FROM categories
WHERE id = $1
  AND household_id = $2;
