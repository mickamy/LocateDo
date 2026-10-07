package usecase_test

import (
	"testing"
	"time"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	hmodel "github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/todo/usecase"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/test/tdb"
	"github.com/mickamy/LocateDo/test/tseed"
)

func TestSetTodoCompletion_completeThenReopen(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	setCompletion := usecase.NewSetTodoCompletion(d.Infra())
	h := d.Seeder.Household(t, hmodel.PlanFree)
	id := d.Seeder.Todo(t, h.ID, d.Seeder.Place(t, h.ID))

	// act & assert
	require.NoError(t, setCompletion.Do(t.Context(), usecase.SetTodoCompletionInput{
		UserID: h.OwnerID, HouseholdID: h.ID, TodoID: id, CompletedAt: &now,
	}))
	got := completedAt(t, d, id, h.ID)
	require.NotNil(t, got)
	assert.True(t, now.Equal(*got))

	require.NoError(t, setCompletion.Do(t.Context(), usecase.SetTodoCompletionInput{
		UserID:      h.OwnerID,
		HouseholdID: h.ID,
		TodoID:      id,
	}))
	assert.Nil(t, completedAt(t, d, id, h.ID))
}

func TestSetTodoCompletion_freeLimitOnReopen(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		plan hmodel.Plan
		want error
	}{
		{name: "free household at the limit", plan: hmodel.PlanFree, want: aerrors.ErrPrecondition},
		{name: "pro household at the limit", plan: hmodel.PlanPro},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			// arrange
			d := tdb.New(t)
			h := d.Seeder.Household(t, tt.plan)
			placeID := d.Seeder.Place(t, h.ID)
			for range hmodel.MaxFreeOpenTodos {
				d.Seeder.Todo(t, h.ID, placeID)
			}
			done := d.Seeder.CompletedTodo(t, h.ID, placeID)

			// act
			err := usecase.NewSetTodoCompletion(d.Infra()).Do(t.Context(), usecase.SetTodoCompletionInput{
				UserID: h.OwnerID, HouseholdID: h.ID, TodoID: done,
			})

			// assert
			if tt.want != nil {
				require.ErrorIs(t, err, tt.want)
				assert.NotNil(t, completedAt(t, d, done, h.ID), "the todo stays completed")
				return
			}
			require.NoError(t, err)
			assert.Nil(t, completedAt(t, d, done, h.ID))
		})
	}
}

func TestSetTodoCompletion_completingIsAlwaysAllowed(t *testing.T) {
	t.Parallel()

	// arrange: a free household already over the limit
	d := tdb.New(t)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	placeID := d.Seeder.Place(t, h.ID)
	var last uuid.UUID
	for range hmodel.MaxFreeOpenTodos + 1 {
		last = d.Seeder.Todo(t, h.ID, placeID)
	}

	// act
	err := usecase.NewSetTodoCompletion(d.Infra()).Do(t.Context(), usecase.SetTodoCompletionInput{
		UserID: h.OwnerID, HouseholdID: h.ID, TodoID: last, CompletedAt: &now,
	})

	// assert
	require.NoError(t, err)
	assert.NotNil(t, completedAt(t, d, last, h.ID))
}

func TestSetTodoCompletion_missingOrForeignIsANoOp(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	other := d.Seeder.Household(t, hmodel.PlanFree)
	foreign := d.Seeder.Todo(t, other.ID, d.Seeder.Place(t, other.ID))
	setCompletion := usecase.NewSetTodoCompletion(d.Infra())

	// act
	missingErr := setCompletion.Do(t.Context(), usecase.SetTodoCompletionInput{
		UserID: h.OwnerID, HouseholdID: h.ID, TodoID: uuid.NewV7(), CompletedAt: &now,
	})
	foreignErr := setCompletion.Do(t.Context(), usecase.SetTodoCompletionInput{
		UserID: h.OwnerID, HouseholdID: h.ID, TodoID: foreign, CompletedAt: &now,
	})

	// assert
	require.NoError(t, missingErr)
	require.NoError(t, foreignErr)
	assert.Nil(t, completedAt(t, d, foreign, other.ID), "another household's todo is left alone")
}

func TestSetTodoCompletion_recordsEachCompletion(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	setCompletion := usecase.NewSetTodoCompletion(d.Infra())
	h := d.Seeder.Household(t, hmodel.PlanPro)
	memberID := d.Seeder.Member(t, h.ID)
	id := d.Seeder.Todo(t, h.ID, d.Seeder.Place(t, h.ID))
	ctx := clock.Set(t.Context(), clock.NewFixed(now.Add(time.Hour)))
	later := now.Add(time.Minute)

	// act: the member completes it (twice, as a retry would), the owner reopens it and completes it
	for _, in := range []usecase.SetTodoCompletionInput{
		{UserID: memberID, HouseholdID: h.ID, TodoID: id, CompletedAt: &now},
		{UserID: memberID, HouseholdID: h.ID, TodoID: id, CompletedAt: &later},
		{UserID: h.OwnerID, HouseholdID: h.ID, TodoID: id},
		{UserID: h.OwnerID, HouseholdID: h.ID, TodoID: id, CompletedAt: &later},
	} {
		require.NoError(t, setCompletion.Do(ctx, in))
	}

	// assert
	got := completions(t, d, id)
	require.Len(t, got, 2, "completing a completed to-do adds no completion")
	assert.Equal(t, &memberID, got[0].completerID)
	assert.True(t, now.Equal(got[0].completedAt))
	require.NotNil(t, got[0].reopenedAt)
	assert.True(t, now.Add(time.Hour).Equal(*got[0].reopenedAt), "reopened at the server's time")
	assert.Equal(t, &h.OwnerID, got[1].completerID)
	assert.True(t, later.Equal(got[1].completedAt))
	assert.Nil(t, got[1].reopenedAt)
}

func TestSetTodoCompletion_queuesANoticeToTheCreator(t *testing.T) {
	t.Parallel()

	// arrange: the member checks off two of the owner's to-dos
	d := tdb.New(t)
	setCompletion := usecase.NewSetTodoCompletion(d.Infra())
	h := d.Seeder.Household(t, hmodel.PlanPro)
	memberID := d.Seeder.Member(t, h.ID)
	placeID := d.Seeder.Place(t, h.ID)
	first := d.Seeder.Todo(t, h.ID, placeID)
	second := d.Seeder.Todo(t, h.ID, placeID)
	createdBy(t, d, h.OwnerID, first, second)
	ctx := clock.Set(t.Context(), clock.NewFixed(now))

	// act
	for _, id := range []uuid.UUID{first, second} {
		require.NoError(t, setCompletion.Do(ctx, usecase.SetTodoCompletionInput{
			UserID: memberID, HouseholdID: h.ID, TodoID: id, CompletedAt: &now,
		}))
	}

	// assert
	got := completionNotices(t, d)
	require.Len(t, got, 1, "completions gather into one notice")
	assert.Equal(t, "completion:"+h.OwnerID.String()+":"+memberID.String(), got[0].dedupeKey)
	assert.True(t, now.Add(2*time.Minute).Equal(got[0].runAt))
}

func TestSetTodoCompletion_queuesNoNotice(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name    string
		creator func(t *testing.T, d tdb.DB, h tseed.Household) *uuid.UUID
	}{
		{name: "own to-do", creator: func(_ *testing.T, _ tdb.DB, h tseed.Household) *uuid.UUID { return &h.OwnerID }},
		{name: "unknown creator", creator: func(*testing.T, tdb.DB, tseed.Household) *uuid.UUID { return nil }},
		{name: "creator left the household", creator: func(t *testing.T, d tdb.DB, _ tseed.Household) *uuid.UUID {
			return new(d.Seeder.User(t))
		}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			// arrange
			d := tdb.New(t)
			h := d.Seeder.Household(t, hmodel.PlanPro)
			id := d.Seeder.Todo(t, h.ID, d.Seeder.Place(t, h.ID))
			if creator := tt.creator(t, d, h); creator != nil {
				createdBy(t, d, *creator, id)
			}

			// act
			err := usecase.NewSetTodoCompletion(d.Infra()).Do(t.Context(), usecase.SetTodoCompletionInput{
				UserID: h.OwnerID, HouseholdID: h.ID, TodoID: id, CompletedAt: &now,
			})

			// assert
			require.NoError(t, err)
			assert.Empty(t, completionNotices(t, d))
		})
	}
}
