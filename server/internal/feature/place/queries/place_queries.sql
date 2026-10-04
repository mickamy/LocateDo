-- name: PlaceExists :one
SELECT EXISTS (SELECT 1
               FROM places
               WHERE id = $1
                 AND household_id = $2);

-- name: CountPlaces :one
SELECT count(*)
FROM places
WHERE household_id = $1;

-- A category id the household does not have resolves to NULL.
-- name: UpsertPlace :exec
INSERT INTO places (id, household_id, name, lat, lng, radius_m, category_id, sort_order)
VALUES (sqlc.arg(id),
        sqlc.arg(household_id),
        sqlc.arg(name),
        sqlc.arg(lat),
        sqlc.arg(lng),
        sqlc.arg(radius_m),
        (SELECT c.id
         FROM categories c
         WHERE c.id = sqlc.narg(category_id)
           AND c.household_id = sqlc.arg(household_id)),
        sqlc.arg(sort_order))
ON CONFLICT (id, household_id) DO UPDATE
    SET name        = EXCLUDED.name,
        lat         = EXCLUDED.lat,
        lng         = EXCLUDED.lng,
        radius_m    = EXCLUDED.radius_m,
        category_id = EXCLUDED.category_id,
        sort_order  = EXCLUDED.sort_order;

-- name: DeletePlace :exec
DELETE
FROM places
WHERE id = $1
  AND household_id = $2;
