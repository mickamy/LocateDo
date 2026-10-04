package repository_test

import (
	"testing"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/category/model"
	"github.com/mickamy/LocateDo/internal/feature/category/repository"
	hmodel "github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestCategory_Upsert_insertThenUpdate(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	categories := repository.NewCategory(d.Reader)
	householdID := d.Seeder.Household(t, hmodel.PlanFree).ID
	shopping := "shopping"
	c := model.Category{
		ID:          uuid.NewV7(),
		HouseholdID: householdID,
		BuiltinKey:  &shopping,
		Icon:        "cart",
		Color:       "green",
		SortOrder:   1,
	}

	// act
	d.InTx(t, func(tx tx.Tx) {
		require.NoError(t, categories.Bind(tx).Upsert(t.Context(), c))
	})
	inserted := readCategory(t, d, c.ID)

	renamed := "Costco"
	c.Name = &renamed
	c.Icon = "store"
	d.InTx(t, func(tx tx.Tx) {
		require.NoError(t, categories.Bind(tx).Upsert(t.Context(), c))
	})
	updated := readCategory(t, d, c.ID)

	// assert
	assert.Equal(t, &shopping, inserted.BuiltinKey)
	assert.Nil(t, inserted.Name, "a built-in keeps its localized name until renamed")
	assert.Equal(t, &renamed, updated.Name)
	assert.Equal(t, "store", updated.Icon)
	assert.Equal(t, &shopping, updated.BuiltinKey)
	assert.Greater(t, updated.Version, inserted.Version)
}

func TestCategory_Upsert_builtinBecomesCustom(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	categories := repository.NewCategory(d.Reader)
	householdID := d.Seeder.Household(t, hmodel.PlanFree).ID
	id := d.Seeder.BuiltinCategory(t, householdID, "work")
	name := "Office"

	// act
	d.InTx(t, func(tx tx.Tx) {
		require.NoError(t, categories.Bind(tx).Upsert(t.Context(), model.Category{
			ID: id, HouseholdID: householdID, Name: &name, Icon: "briefcase", Color: "blue",
		}))
	})

	// assert
	row := readCategory(t, d, id)
	assert.Nil(t, row.BuiltinKey)
	assert.Equal(t, &name, row.Name)
}

func TestCategory_Upsert_conflicts(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name    string
		arrange func(t *testing.T, d tdb.DB, householdID uuid.UUID) model.Category
	}{
		{
			name: "second built-in with the same key",
			arrange: func(t *testing.T, d tdb.DB, householdID uuid.UUID) model.Category {
				d.Seeder.BuiltinCategory(t, householdID, "shopping")
				shopping := "shopping"
				return model.Category{
					ID: uuid.NewV7(), HouseholdID: householdID, BuiltinKey: &shopping, Icon: "cart", Color: "green",
				}
			},
		},
		{
			name: "id in another household",
			arrange: func(t *testing.T, d tdb.DB, householdID uuid.UUID) model.Category {
				taken := d.Seeder.Category(t, d.Seeder.Household(t, hmodel.PlanFree).ID)
				name := "Drugstore"
				return model.Category{ID: taken, HouseholdID: householdID, Name: &name, Icon: "cart", Color: "green"}
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			// arrange
			d := tdb.New(t)
			categories := repository.NewCategory(d.Reader)
			householdID := d.Seeder.Household(t, hmodel.PlanFree).ID
			c := tt.arrange(t, d, householdID)

			// act
			err := d.Transactor.WithTx(t.Context(), func(tx tx.Tx) error {
				return categories.Bind(tx).Upsert(t.Context(), c)
			})

			// assert
			require.ErrorIs(t, err, aerrors.ErrConflict)
		})
	}
}

func TestCategory_Upsert_unknownHousehold(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	categories := repository.NewCategory(d.Reader)
	name := "Drugstore"

	// act
	err := d.Transactor.WithTx(t.Context(), func(tx tx.Tx) error {
		return categories.Bind(tx).Upsert(t.Context(), model.Category{
			ID: uuid.NewV7(), HouseholdID: uuid.NewV7(), Name: &name, Icon: "cart", Color: "green",
		})
	})

	// assert
	require.ErrorIs(t, err, aerrors.ErrInvalidArgument)
}

func TestCategory_Delete(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	categories := repository.NewCategory(d.Reader)
	householdID := d.Seeder.Household(t, hmodel.PlanFree).ID
	id := d.Seeder.Category(t, householdID)
	placeID := d.Seeder.CategorizedPlace(t, householdID, id)
	other := d.Seeder.Household(t, hmodel.PlanFree).ID
	elsewhere := d.Seeder.Category(t, other)

	// act
	d.InTx(t, func(tx tx.Tx) {
		bound := categories.Bind(tx)
		require.NoError(t, bound.Delete(t.Context(), id, householdID))
		require.NoError(t, bound.Delete(t.Context(), id, householdID), "deleting again is a no-op")
		require.NoError(t, bound.Delete(t.Context(), elsewhere, householdID), "another household's row is left alone")
	})

	// assert
	assert.Zero(t, d.Seeder.Count(t, "categories", householdID))
	assert.Equal(t, 1, d.Seeder.Count(t, "categories", other))
	var categoryID *uuid.UUID
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"SELECT category_id FROM places WHERE id = $1", placeID).Scan(&categoryID))
	assert.Nil(t, categoryID, "its places become uncategorized")
}

type categoryRow struct {
	BuiltinKey *string
	Name       *string
	Icon       string
	Version    int64
}

func readCategory(t *testing.T, d tdb.DB, id uuid.UUID) categoryRow {
	t.Helper()

	var row categoryRow
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"SELECT builtin_key, name, icon, version FROM categories WHERE id = $1", id).
		Scan(&row.BuiltinKey, &row.Name, &row.Icon, &row.Version))
	return row
}
