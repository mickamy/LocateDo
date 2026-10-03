package migrate_test

import (
	"testing"
	"time"

	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/test/tdb"
)

const uniqueViolationCode = "23505"

func TestMemberships_oneHouseholdPerUser(t *testing.T) {
	t.Parallel()

	// arrange
	w := tdb.New(t).Writer
	first := createHousehold(t, w)
	second := createHousehold(t, w)
	var userID string
	require.NoError(t, w.QueryRow(t.Context(), "INSERT INTO users DEFAULT VALUES RETURNING id").Scan(&userID))
	_, err := w.Exec(t.Context(),
		"INSERT INTO memberships (household_id, user_id, role) VALUES ($1, $2, 'member')", first, userID)
	require.NoError(t, err)

	// act
	_, err = w.Exec(t.Context(),
		"INSERT INTO memberships (household_id, user_id, role) VALUES ($1, $2, 'member')", second, userID)

	// assert
	assertPgCode(t, err, uniqueViolationCode)
}

func TestHouseholdInvites_tokenHashIsUnique(t *testing.T) {
	t.Parallel()

	// arrange
	w := tdb.New(t).Writer
	householdID := createHousehold(t, w)
	var ownerID string
	require.NoError(t, w.QueryRow(t.Context(),
		"SELECT owner_id FROM households WHERE id = $1", householdID).Scan(&ownerID))
	insert := `INSERT INTO household_invites (household_id, token_hash, created_by, expires_at)
	           VALUES ($1, 'same-hash', $2, $3)`
	_, err := w.Exec(t.Context(), insert, householdID, ownerID, time.Now().Add(72*time.Hour))
	require.NoError(t, err)

	// act
	_, err = w.Exec(t.Context(), insert, householdID, ownerID, time.Now().Add(72*time.Hour))

	// assert
	assertPgCode(t, err, uniqueViolationCode)
}
