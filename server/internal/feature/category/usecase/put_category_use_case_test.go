package usecase_test

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/category/fixture"
	"github.com/mickamy/LocateDo/internal/feature/category/model"
	"github.com/mickamy/LocateDo/internal/feature/category/usecase"
	hmodel "github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestPutCategory(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	name := "Drugstore"
	c := fixture.Category(func(m *model.Category) { m.HouseholdID = h.ID; m.Name = &name })

	// act
	err := usecase.NewPutCategory(d.Infra()).Do(t.Context(), usecase.PutCategoryInput{HouseholdID: h.ID, Category: c})

	// assert
	require.NoError(t, err)
	assert.Equal(t, 1, d.Seeder.Count(t, "categories", h.ID))
}

func TestPutCategory_anotherHousehold(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	other := d.Seeder.Household(t, hmodel.PlanFree)
	name := "Drugstore"
	c := fixture.Category(func(m *model.Category) { m.HouseholdID = other.ID; m.Name = &name })

	// act
	err := usecase.NewPutCategory(d.Infra()).Do(t.Context(), usecase.PutCategoryInput{HouseholdID: h.ID, Category: c})

	// assert
	require.ErrorIs(t, err, aerrors.ErrPermissionDenied)
	assert.Zero(t, d.Seeder.Count(t, "categories", other.ID))
}

func TestPutCategory_builtinAgainAfterDelete(t *testing.T) {
	t.Parallel()

	// arrange: the household's built-in shopping category was deleted
	d := tdb.New(t)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	deleted := d.Seeder.BuiltinCategory(t, h.ID, "shopping")
	require.NoError(t, usecase.NewDeleteCategory(d.Infra()).Do(t.Context(),
		usecase.DeleteCategoryInput{HouseholdID: h.ID, CategoryID: deleted}))
	shopping := "shopping"
	c := fixture.Category(func(m *model.Category) { m.HouseholdID = h.ID; m.BuiltinKey = &shopping; m.Name = nil })

	// act: a new id with the same builtin key
	err := usecase.NewPutCategory(d.Infra()).Do(t.Context(), usecase.PutCategoryInput{HouseholdID: h.ID, Category: c})

	// assert
	require.NoError(t, err)
	assert.Equal(t, 1, d.Seeder.Count(t, "categories", h.ID))
}
