package ids_test

import (
	"testing"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/lib/ids"
)

func TestParse(t *testing.T) {
	t.Parallel()

	id := uuid.NewV7()

	got, err := ids.Parse("id", id.String())

	require.NoError(t, err)
	assert.Equal(t, id, got)
}

func TestParse_invalid(t *testing.T) {
	t.Parallel()

	_, err := ids.Parse("household_id", "not-a-uuid")

	require.ErrorIs(t, err, aerrors.ErrInvalidArgument)
	assert.ErrorContains(t, err, "household_id")
}
