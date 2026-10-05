package usecase_test

import (
	"testing"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/feature/category/usecase"
	hmodel "github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestDeleteCategory(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	deleteCategory := usecase.NewDeleteCategory(d.Infra())
	h := d.Seeder.Household(t, hmodel.PlanFree)
	categoryID := d.Seeder.Category(t, h.ID)
	placeID := d.Seeder.CategorizedPlace(t, h.ID, categoryID)

	// act
	err := deleteCategory.Do(t.Context(), usecase.DeleteCategoryInput{HouseholdID: h.ID, CategoryID: categoryID})
	again := deleteCategory.Do(t.Context(), usecase.DeleteCategoryInput{HouseholdID: h.ID, CategoryID: categoryID})

	// assert
	require.NoError(t, err)
	require.NoError(t, again, "a retry after the category is gone succeeds")
	assert.Zero(t, d.Seeder.Count(t, "categories", h.ID))
	var placeCategory *uuid.UUID
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"SELECT category_id FROM places WHERE id = $1", placeID).Scan(&placeCategory))
	assert.Nil(t, placeCategory, "its places become uncategorized")
}

func TestDeleteCategory_anotherHousehold(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	other := d.Seeder.Household(t, hmodel.PlanFree)
	categoryID := d.Seeder.Category(t, other.ID)

	// act
	err := usecase.NewDeleteCategory(d.Infra()).Do(t.Context(), usecase.DeleteCategoryInput{
		HouseholdID: h.ID, CategoryID: categoryID,
	})

	// assert
	require.NoError(t, err)
	assert.Equal(t, 1, d.Seeder.Count(t, "categories", other.ID))
}

func TestDeleteCategory_placesChangeForOtherDevices(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	categoryID := d.Seeder.Category(t, h.ID)
	placeID := d.Seeder.CategorizedPlace(t, h.ID, categoryID)
	before := placeVersion(t, d, placeID)

	// act
	err := usecase.NewDeleteCategory(d.Infra()).Do(t.Context(),
		usecase.DeleteCategoryInput{HouseholdID: h.ID, CategoryID: categoryID})

	// assert: the place gets a new version, so other devices pull it as a change
	require.NoError(t, err)
	assert.Greater(t, placeVersion(t, d, placeID), before)
}

func placeVersion(t *testing.T, d tdb.DB, placeID uuid.UUID) int64 {
	t.Helper()

	var v int64
	require.NoError(t, d.Writer.QueryRow(t.Context(), "SELECT version FROM places WHERE id = $1", placeID).Scan(&v))
	return v
}
