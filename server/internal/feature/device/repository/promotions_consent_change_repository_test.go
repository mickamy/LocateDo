package repository_test

import (
	"testing"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/feature/device/model"
	"github.com/mickamy/LocateDo/internal/feature/device/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestPromotionsConsentChange_Insert(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	changes := repository.NewPromotionsConsentChange(d.Reader)
	userID := d.Seeder.User(t)
	change := model.PromotionsConsentChange{
		DeviceID:  uuid.NewV7(),
		UserID:    &userID,
		Consented: true,
		ChangedAt: now,
	}

	// act
	d.InTx(t, func(tx tx.Tx) {
		require.NoError(t, changes.Bind(tx).Insert(t.Context(), change))
	})

	// assert
	var got model.PromotionsConsentChange
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"SELECT device_id, user_id, consented, changed_at FROM promotions_consent_changes").
		Scan(&got.DeviceID, &got.UserID, &got.Consented, &got.ChangedAt))
	assert.Equal(t, change.DeviceID, got.DeviceID)
	assert.Equal(t, change.UserID, got.UserID)
	assert.True(t, got.Consented)
	assert.True(t, now.Equal(got.ChangedAt))
}

func TestPromotionsConsentChange_outlivesTheUser(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	changes := repository.NewPromotionsConsentChange(d.Reader)
	userID := d.Seeder.User(t)
	d.InTx(t, func(tx tx.Tx) {
		require.NoError(t, changes.Bind(tx).Insert(t.Context(), model.PromotionsConsentChange{
			DeviceID: uuid.NewV7(), UserID: &userID, Consented: true, ChangedAt: now,
		}))
	})

	// act
	_, err := d.Writer.Exec(t.Context(), "DELETE FROM users WHERE id = $1", userID)
	require.NoError(t, err)

	// assert
	var owner *uuid.UUID
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"SELECT user_id FROM promotions_consent_changes").Scan(&owner))
	assert.Nil(t, owner, "deleting the account unlinks the record but keeps it")
}
