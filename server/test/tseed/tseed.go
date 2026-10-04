// Package tseed writes the rows a test needs before it starts.
package tseed

import (
	"testing"
	"uuid"

	"github.com/stretchr/testify/require"

	hmodel "github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/infra/storage/db"
)

type Seeder struct {
	w db.Writer
}

func New(w db.Writer) Seeder {
	return Seeder{w: w}
}

type Household struct {
	ID      uuid.UUID
	OwnerID uuid.UUID
}

func (s Seeder) User(t *testing.T) uuid.UUID {
	t.Helper()

	return s.insert(t, "INSERT INTO users DEFAULT VALUES RETURNING id")
}

// Household creates a household whose owner is already a member.
func (s Seeder) Household(t *testing.T, plan hmodel.Plan) Household {
	t.Helper()

	ownerID := s.User(t)
	id := s.insert(t, "INSERT INTO households (owner_id, plan) VALUES ($1, $2) RETURNING id", ownerID, string(plan))
	s.exec(t, "INSERT INTO memberships (household_id, user_id, role) VALUES ($1, $2, $3)",
		id, ownerID, string(hmodel.RoleOwner))
	return Household{ID: id, OwnerID: ownerID}
}

func (s Seeder) Member(t *testing.T, householdID uuid.UUID) uuid.UUID {
	t.Helper()

	userID := s.User(t)
	s.exec(t, "INSERT INTO memberships (household_id, user_id, role) VALUES ($1, $2, $3)",
		householdID, userID, string(hmodel.RoleMember))
	return userID
}

func (s Seeder) SetPlan(t *testing.T, householdID uuid.UUID, plan hmodel.Plan) {
	t.Helper()

	s.exec(t, "UPDATE households SET plan = $1 WHERE id = $2", string(plan), householdID)
}

func (s Seeder) Category(t *testing.T, householdID uuid.UUID) uuid.UUID {
	t.Helper()

	return s.insert(t, `INSERT INTO categories (household_id, name, icon, color)
		VALUES ($1, 'Drugstore', 'cart', 'green') RETURNING id`, householdID)
}

func (s Seeder) BuiltinCategory(t *testing.T, householdID uuid.UUID, key string) uuid.UUID {
	t.Helper()

	return s.insert(t, `INSERT INTO categories (household_id, builtin_key, icon, color)
		VALUES ($1, $2, 'cart', 'green') RETURNING id`, householdID, key)
}

func (s Seeder) Place(t *testing.T, householdID uuid.UUID) uuid.UUID {
	t.Helper()

	return s.insert(t, `INSERT INTO places (household_id, name, lat, lng)
		VALUES ($1, 'store', 35.0, 139.0) RETURNING id`, householdID)
}

func (s Seeder) CategorizedPlace(t *testing.T, householdID, categoryID uuid.UUID) uuid.UUID {
	t.Helper()

	return s.insert(t, `INSERT INTO places (household_id, name, lat, lng, category_id)
		VALUES ($1, 'store', 35.0, 139.0, $2) RETURNING id`, householdID, categoryID)
}

func (s Seeder) Todo(t *testing.T, householdID, placeID uuid.UUID) uuid.UUID {
	t.Helper()

	return s.insert(t, "INSERT INTO todos (household_id, place_id, title) VALUES ($1, $2, 'milk') RETURNING id",
		householdID, placeID)
}

func (s Seeder) CompletedTodo(t *testing.T, householdID, placeID uuid.UUID) uuid.UUID {
	t.Helper()

	return s.insert(t, `INSERT INTO todos (household_id, place_id, title, completed_at)
		VALUES ($1, $2, 'milk', now()) RETURNING id`, householdID, placeID)
}

// Count returns how many rows of table belong to the household.
func (s Seeder) Count(t *testing.T, table string, householdID uuid.UUID) int {
	t.Helper()

	var n int
	require.NoError(t, s.w.QueryRow(t.Context(),
		"SELECT count(*) FROM "+table+" WHERE household_id = $1", householdID).Scan(&n))
	return n
}

func (s Seeder) Version(t *testing.T, householdID uuid.UUID) int64 {
	t.Helper()

	var v int64
	require.NoError(t, s.w.QueryRow(t.Context(),
		"SELECT version FROM households WHERE id = $1", householdID).Scan(&v))
	return v
}

func (s Seeder) insert(t *testing.T, sql string, args ...any) uuid.UUID {
	t.Helper()

	var id uuid.UUID
	require.NoError(t, s.w.QueryRow(t.Context(), sql, args...).Scan(&id))
	return id
}

func (s Seeder) exec(t *testing.T, sql string, args ...any) {
	t.Helper()

	_, err := s.w.Exec(t.Context(), sql, args...)
	require.NoError(t, err)
}
