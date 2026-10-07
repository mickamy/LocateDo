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

type completion struct {
	completerID *uuid.UUID
	completedAt time.Time
	reopenedAt  *time.Time
}

func completions(t *testing.T, d tdb.DB, todoID uuid.UUID) []completion {
	t.Helper()

	rows, err := d.Writer.Query(t.Context(),
		"SELECT completer_id, completed_at, reopened_at FROM todo_completions WHERE todo_id = $1 ORDER BY id", todoID)
	require.NoError(t, err)
	defer rows.Close()
	var out []completion
	for rows.Next() {
		var c completion
		require.NoError(t, rows.Scan(&c.completerID, &c.completedAt, &c.reopenedAt))
		out = append(out, c)
	}
	require.NoError(t, rows.Err())
	return out
}
