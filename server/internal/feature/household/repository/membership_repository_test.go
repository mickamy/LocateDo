package repository_test

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/household/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestMembership_lifecycle(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	households := repository.NewHousehold(d.Reader)
	memberships := repository.NewMembership(d.Reader)
	ownerID := d.Seed.User(t)
	householdID := createHousehold(t, d, households, ownerID)
	memberID := d.Seed.User(t)

	// act
	d.InTx(t, func(tx tx.Tx) {
		for _, m := range []model.Membership{
			{HouseholdID: householdID, UserID: ownerID, Role: model.RoleOwner},
			{HouseholdID: householdID, UserID: memberID, Role: model.RoleMember},
		} {
			require.NoError(t, memberships.Bind(tx).Create(t.Context(), m))
		}
	})

	// assert
	got, err := memberships.FindByUser(t.Context(), memberID)
	require.NoError(t, err)
	assert.Equal(t, householdID, got.HouseholdID)
	assert.Equal(t, model.RoleMember, got.Role)

	n, err := memberships.Count(t.Context(), householdID)
	require.NoError(t, err)
	assert.Equal(t, 2, n)

	d.InTx(t, func(tx tx.Tx) {
		require.NoError(t, memberships.Bind(tx).Delete(t.Context(), householdID, memberID))
		require.ErrorIs(t, memberships.Bind(tx).Delete(t.Context(), householdID, memberID), aerrors.ErrNotFound)
	})
	_, err = memberships.FindByUser(t.Context(), memberID)
	require.ErrorIs(t, err, aerrors.ErrNotFound)
}

func TestMembership_Create_userAlreadyInAHousehold(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	households := repository.NewHousehold(d.Reader)
	memberships := repository.NewMembership(d.Reader)
	userID := d.Seed.User(t)
	first := createHousehold(t, d, households, d.Seed.User(t))
	second := createHousehold(t, d, households, d.Seed.User(t))
	d.InTx(t, func(tx tx.Tx) {
		require.NoError(t, memberships.Bind(tx).Create(t.Context(), model.Membership{
			HouseholdID: first, UserID: userID, Role: model.RoleMember,
		}))
	})

	// act
	err := d.Transactor.WithTx(t.Context(), func(tx tx.Tx) error {
		return memberships.Bind(tx).Create(t.Context(), model.Membership{
			HouseholdID: second, UserID: userID, Role: model.RoleMember,
		})
	})

	// assert
	require.ErrorIs(t, err, aerrors.ErrConflict)
}
