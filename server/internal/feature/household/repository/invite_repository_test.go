package repository_test

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/household/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestInvite_acceptOnce(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	invites := repository.NewInvite(d.Reader)
	ownerID := d.Seeder.User(t)
	householdID := createHousehold(t, d, repository.NewHousehold(d.Reader), ownerID)
	inviteeID := d.Seeder.User(t)
	hash := []byte("invite-hash")
	d.InTx(t, func(tx tx.Tx) {
		require.NoError(t, invites.Bind(tx).Create(t.Context(), model.Invite{
			HouseholdID: householdID,
			CreatedBy:   ownerID,
			ExpiresAt:   now.Add(72 * time.Hour),
		}, hash))
	})

	// act
	var accepted model.Invite
	var first, second, unknown error
	d.InTx(t, func(tx tx.Tx) {
		accepted, first = invites.Bind(tx).Accept(t.Context(), hash, inviteeID, now)
		_, second = invites.Bind(tx).Accept(t.Context(), hash, d.Seeder.User(t), now)
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
