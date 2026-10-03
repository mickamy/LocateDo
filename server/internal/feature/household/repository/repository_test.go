package repository_test

import (
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/household/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/test/tdb"
)

var now = time.Date(2026, 10, 3, 12, 0, 0, 0, time.UTC)

func TestHousehold_createFindDelete(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	households := repository.NewHousehold(d.Reader)
	ownerID := createUser(t, d)
	id := uuid.Must(uuid.NewV7())

	// act
	var created model.Household
	inTx(t, d, func(tx tx.Tx) {
		var err error
		created, err = households.Bind(tx).Create(t.Context(), id, ownerID)
		require.NoError(t, err)
	})
	found, err := households.Find(t.Context(), id)

	// assert
	require.NoError(t, err)
	assert.Equal(t, id, created.ID)
	assert.Equal(t, model.PlanFree, created.Plan)
	assert.Equal(t, created, found)

	inTx(t, d, func(tx tx.Tx) {
		require.NoError(t, households.Bind(tx).Delete(t.Context(), id))
		require.ErrorIs(t, households.Bind(tx).Delete(t.Context(), id), aerrors.ErrNotFound)
	})
	_, err = households.Find(t.Context(), id)
	require.ErrorIs(t, err, aerrors.ErrNotFound)
}

func TestHousehold_Create_duplicateID(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	households := repository.NewHousehold(d.Reader)
	id := createHousehold(t, d, households, createUser(t, d))

	// act
	err := d.Transactor.WithTx(t.Context(), func(tx tx.Tx) error {
		_, err := households.Bind(tx).Create(t.Context(), id, createUser(t, d))
		return err
	})

	// assert
	require.ErrorIs(t, err, aerrors.ErrConflict)
}

func TestMembership_lifecycle(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	households := repository.NewHousehold(d.Reader)
	memberships := repository.NewMembership(d.Reader)
	ownerID := createUser(t, d)
	householdID := createHousehold(t, d, households, ownerID)
	memberID := createUser(t, d)

	// act
	inTx(t, d, func(tx tx.Tx) {
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

	inTx(t, d, func(tx tx.Tx) {
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
	userID := createUser(t, d)
	first := createHousehold(t, d, households, createUser(t, d))
	second := createHousehold(t, d, households, createUser(t, d))
	inTx(t, d, func(tx tx.Tx) {
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

func TestInvite_acceptOnce(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	invites := repository.NewInvite(d.Reader)
	ownerID := createUser(t, d)
	householdID := createHousehold(t, d, repository.NewHousehold(d.Reader), ownerID)
	inviteeID := createUser(t, d)
	hash := []byte("invite-hash")
	inTx(t, d, func(tx tx.Tx) {
		require.NoError(t, invites.Bind(tx).Create(t.Context(), model.Invite{
			HouseholdID: householdID,
			CreatedBy:   ownerID,
			ExpiresAt:   now.Add(72 * time.Hour),
		}, hash))
	})

	// act
	var accepted model.Invite
	var first, second, unknown error
	inTx(t, d, func(tx tx.Tx) {
		accepted, first = invites.Bind(tx).Accept(t.Context(), hash, inviteeID, now)
		_, second = invites.Bind(tx).Accept(t.Context(), hash, createUser(t, d), now)
		_, unknown = invites.Bind(tx).Accept(t.Context(), []byte("unknown"), inviteeID, now)
	})

	// assert
	require.NoError(t, first)
	assert.Equal(t, householdID, accepted.HouseholdID)
	assert.Equal(t, ownerID, accepted.CreatedBy)
	assert.True(t, now.Add(72*time.Hour).Equal(accepted.ExpiresAt))
	require.ErrorIs(t, second, aerrors.ErrNotFound)
	require.ErrorIs(t, unknown, aerrors.ErrNotFound)
}

func createUser(t *testing.T, d tdb.DB) uuid.UUID {
	t.Helper()

	var id uuid.UUID
	require.NoError(t, d.Writer.QueryRow(t.Context(), "INSERT INTO users DEFAULT VALUES RETURNING id").Scan(&id))
	return id
}

func createHousehold(t *testing.T, d tdb.DB, households repository.Household, ownerID uuid.UUID) uuid.UUID {
	t.Helper()

	id := uuid.Must(uuid.NewV7())
	inTx(t, d, func(tx tx.Tx) {
		_, err := households.Bind(tx).Create(t.Context(), id, ownerID)
		require.NoError(t, err)
	})
	return id
}

func inTx(t *testing.T, d tdb.DB, fn func(tx tx.Tx)) {
	t.Helper()

	require.NoError(t, d.Transactor.WithTx(t.Context(), func(tx tx.Tx) error {
		fn(tx)
		return nil
	}))
}
