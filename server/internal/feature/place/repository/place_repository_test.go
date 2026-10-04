package repository_test

import (
	"testing"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/place/model"
	"github.com/mickamy/LocateDo/internal/feature/place/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestPlace_Upsert_insertThenUpdate(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	places := repository.NewPlace(d.Reader)
	householdID := createHousehold(t, d)
	categoryID := createCategory(t, d, householdID)
	p := model.Place{
		ID:          uuid.NewV7(),
		HouseholdID: householdID,
		Name:        "Supermarket",
		Lat:         35.6812,
		Lng:         139.7671,
		RadiusM:     100,
		CategoryID:  &categoryID,
		SortOrder:   1,
	}

	// act
	inTx(t, d, func(tx tx.Tx) {
		require.NoError(t, places.Bind(tx).Upsert(t.Context(), p))
	})
	inserted := readPlace(t, d, p.ID)

	p.Name = "Grocery"
	p.RadiusM = 200
	p.CategoryID = nil
	inTx(t, d, func(tx tx.Tx) {
		require.NoError(t, places.Bind(tx).Upsert(t.Context(), p))
	})
	updated := readPlace(t, d, p.ID)

	// assert
	assert.Equal(t, "Supermarket", inserted.Name)
	assert.Equal(t, &categoryID, inserted.CategoryID)
	assert.Equal(t, "Grocery", updated.Name)
	assert.Equal(t, int32(200), updated.RadiusM)
	assert.Nil(t, updated.CategoryID)
	assert.Equal(t, householdID, updated.HouseholdID)
	assert.Greater(t, updated.Version, inserted.Version)
}

func TestPlace_Upsert_unknownCategoryBecomesNull(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	places := repository.NewPlace(d.Reader)
	householdID := createHousehold(t, d)
	otherCategory := createCategory(t, d, createHousehold(t, d))
	missing := uuid.NewV7()

	tests := map[string]*uuid.UUID{
		"another household's category": &otherCategory,
		"deleted category":             &missing,
	}
	for name, categoryID := range tests {
		t.Run(name, func(t *testing.T) {
			t.Parallel()

			p := model.Place{
				ID:          uuid.NewV7(),
				HouseholdID: householdID,
				Name:        "Supermarket",
				Lat:         35.0,
				Lng:         139.0,
				RadiusM:     100,
				CategoryID:  categoryID,
			}

			// act
			inTx(t, d, func(tx tx.Tx) {
				require.NoError(t, places.Bind(tx).Upsert(t.Context(), p))
			})

			// assert
			assert.Nil(t, readPlace(t, d, p.ID).CategoryID)
		})
	}
}

func TestPlace_Upsert_idInAnotherHousehold(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	places := repository.NewPlace(d.Reader)
	otherHousehold := createHousehold(t, d)
	taken := createPlace(t, d, otherHousehold)
	householdID := createHousehold(t, d)

	// act
	err := d.Transactor.WithTx(t.Context(), func(tx tx.Tx) error {
		return places.Bind(tx).Upsert(t.Context(), model.Place{
			ID:          taken,
			HouseholdID: householdID,
			Name:        "Supermarket",
			Lat:         35.0,
			Lng:         139.0,
			RadiusM:     100,
		})
	})

	// assert
	require.ErrorIs(t, err, aerrors.ErrConflict)
	assert.Equal(t, otherHousehold, readPlace(t, d, taken).HouseholdID)
}

func TestPlace_Upsert_unknownHousehold(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	places := repository.NewPlace(d.Reader)

	// act
	err := d.Transactor.WithTx(t.Context(), func(tx tx.Tx) error {
		return places.Bind(tx).Upsert(t.Context(), model.Place{
			ID:          uuid.NewV7(),
			HouseholdID: uuid.NewV7(),
			Name:        "Supermarket",
			Lat:         35.0,
			Lng:         139.0,
			RadiusM:     100,
		})
	})

	// assert
	require.ErrorIs(t, err, aerrors.ErrInvalidArgument)
}

func TestPlace_ExistsAndCount(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	places := repository.NewPlace(d.Reader)
	householdID := createHousehold(t, d)
	otherHousehold := createHousehold(t, d)
	first := createPlace(t, d, householdID)
	createPlace(t, d, householdID)
	elsewhere := createPlace(t, d, otherHousehold)

	// act
	n, err := places.Count(t.Context(), householdID)
	require.NoError(t, err)
	exists, err := places.Exists(t.Context(), first, householdID)
	require.NoError(t, err)
	wrongHousehold, err := places.Exists(t.Context(), elsewhere, householdID)
	require.NoError(t, err)

	// assert
	assert.Equal(t, 2, n)
	assert.True(t, exists)
	assert.False(t, wrongHousehold)
}

func TestPlace_Delete(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	places := repository.NewPlace(d.Reader)
	householdID := createHousehold(t, d)
	otherHousehold := createHousehold(t, d)
	id := createPlace(t, d, householdID)
	elsewhere := createPlace(t, d, otherHousehold)

	// act
	inTx(t, d, func(tx tx.Tx) {
		bound := places.Bind(tx)
		require.NoError(t, bound.Delete(t.Context(), id, householdID))
		require.NoError(t, bound.Delete(t.Context(), id, householdID), "deleting again is a no-op")
		require.NoError(t, bound.Delete(t.Context(), elsewhere, householdID), "another household's row is left alone")
	})

	// assert
	exists, err := places.Exists(t.Context(), id, householdID)
	require.NoError(t, err)
	assert.False(t, exists)
	assert.Equal(t, otherHousehold, readPlace(t, d, elsewhere).HouseholdID)
}

type placeRow struct {
	HouseholdID uuid.UUID
	Name        string
	RadiusM     int32
	CategoryID  *uuid.UUID
	Version     int64
}

func readPlace(t *testing.T, d tdb.DB, id uuid.UUID) placeRow {
	t.Helper()

	var row placeRow
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"SELECT household_id, name, radius_m, category_id, version FROM places WHERE id = $1", id).
		Scan(&row.HouseholdID, &row.Name, &row.RadiusM, &row.CategoryID, &row.Version))
	return row
}
