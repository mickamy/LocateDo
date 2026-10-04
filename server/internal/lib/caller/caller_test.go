package caller_test

import (
	"testing"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/lib/caller"
)

func TestUserID(t *testing.T) {
	t.Parallel()

	id := uuid.NewV7()

	got, err := caller.UserID(caller.Set(t.Context(), id))

	require.NoError(t, err)
	assert.Equal(t, id, got)
}

func TestUserID_missing(t *testing.T) {
	t.Parallel()

	_, err := caller.UserID(t.Context())

	require.ErrorIs(t, err, aerrors.ErrUnauthenticated)
}

func TestHouseholdID(t *testing.T) {
	t.Parallel()

	id := uuid.NewV7()

	got, err := caller.HouseholdID(caller.SetHousehold(t.Context(), id))

	require.NoError(t, err)
	assert.Equal(t, id, got)
}

func TestHouseholdID_missing(t *testing.T) {
	t.Parallel()

	_, err := caller.HouseholdID(t.Context())

	require.ErrorIs(t, err, aerrors.ErrPermissionDenied)
}
