package usecase_test

import (
	"context"
	"testing"
	"time"
	"uuid"

	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/household/usecase"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/test/tdb"
	"github.com/mickamy/LocateDo/test/tseed"
)

var now = time.Date(2026, 10, 3, 12, 0, 0, 0, time.UTC)

func fixedClock(t *testing.T) context.Context {
	t.Helper()

	return clock.Set(t.Context(), clock.NewFixed(now))
}

func invite(t *testing.T, d tdb.DB, h tseed.Household) string {
	t.Helper()

	out, err := usecase.NewCreateInvite(d.Infra()).Do(fixedClock(t), usecase.CreateInviteInput{
		UserID: h.OwnerID, HouseholdID: h.ID,
	})
	require.NoError(t, err)
	return out.Token
}

func contents() model.Contents {
	shopping := "shopping"
	categoryID := newID()
	placeID := newID()
	completedAt := now.Add(-time.Hour)
	return model.Contents{
		Categories: []model.ImportCategory{
			{ID: categoryID, BuiltinKey: &shopping, Icon: "cart", Color: "green"},
		},
		Places: []model.ImportPlace{
			{ID: placeID, Name: "Supermarket", Lat: 35.0, Lng: 139.0, RadiusM: 100, CategoryID: &categoryID},
		},
		Todos: []model.InitialTodo{
			{Todo: model.ImportTodo{ID: newID(), PlaceID: placeID, Title: "Milk"}},
			{Todo: model.ImportTodo{ID: newID(), PlaceID: placeID, Title: "Detergent"}, CompletedAt: &completedAt},
		},
	}
}

func newID() uuid.UUID {
	return uuid.NewV7()
}
