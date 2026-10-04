package usecase_test

import (
	"testing"
	"time"
	"uuid"

	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/feature/todo/fixture"
	"github.com/mickamy/LocateDo/internal/feature/todo/model"
	"github.com/mickamy/LocateDo/internal/feature/todo/repository"
	"github.com/mickamy/LocateDo/test/tdb"
)

var now = time.Date(2026, 10, 4, 12, 0, 0, 0, time.UTC)

func todoAt(householdID, placeID uuid.UUID) model.Todo {
	return fixture.Todo(func(m *model.Todo) {
		m.HouseholdID = householdID
		m.PlaceID = placeID
	})
}

func completedAt(t *testing.T, d tdb.DB, id, householdID uuid.UUID) *time.Time {
	t.Helper()

	got, err := repository.NewTodo(d.Reader).Find(t.Context(), id, householdID)
	require.NoError(t, err)
	return got.CompletedAt
}
