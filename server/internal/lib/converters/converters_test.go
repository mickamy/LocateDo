package converters_test

import (
	"testing"
	"time"
	"uuid"

	"github.com/stretchr/testify/assert"

	"github.com/mickamy/LocateDo/internal/lib/converters"
)

func TestUUIDToString(t *testing.T) {
	t.Parallel()

	id := uuid.MustParse("0199a6f0-0000-7000-8000-000000000001")

	assert.Equal(t, "0199a6f0-0000-7000-8000-000000000001", converters.UUIDToString(id))
}

func TestTimestamp_roundTrip(t *testing.T) {
	t.Parallel()

	at := time.Date(2026, 10, 3, 12, 34, 56, 789, time.UTC)

	got := converters.TimestampToTime(converters.TimeToTimestamp(at))

	assert.True(t, at.Equal(got))
}

func TestTimestampToTime_nil(t *testing.T) {
	t.Parallel()

	assert.True(t, converters.TimestampToTime(nil).IsZero())
}
