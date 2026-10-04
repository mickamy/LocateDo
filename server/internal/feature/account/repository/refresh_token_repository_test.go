package repository_test

import (
	"testing"
	"time"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/account/model"
	"github.com/mickamy/LocateDo/internal/feature/account/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestRefreshToken_useOnce(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	u := createUser(t, d, repository.NewUser(d.Reader), "apple-sub")
	tokens := repository.NewRefreshToken(d.Reader)
	familyID := uuid.NewV7()
	hash := []byte("hash-1")
	inTx(t, d, func(tx tx.Tx) {
		require.NoError(t, tokens.Bind(tx).Create(t.Context(), model.RefreshToken{
			UserID:    u.ID,
			FamilyID:  familyID,
			ExpiresAt: now.Add(time.Hour),
		}, hash))
	})

	// act
	var first, second error
	var used model.RefreshToken
	inTx(t, d, func(tx tx.Tx) {
		used, first = tokens.Bind(tx).Use(t.Context(), hash, now)
		_, second = tokens.Bind(tx).Use(t.Context(), hash, now)
	})

	// assert
	require.NoError(t, first)
	assert.Equal(t, u.ID, used.UserID)
	assert.Equal(t, familyID, used.FamilyID)
	require.NotNil(t, used.UsedAt)
	assert.True(t, now.Equal(*used.UsedAt))
	require.ErrorIs(t, second, aerrors.ErrNotFound)

	found, err := tokens.FindByHash(t.Context(), hash)
	require.NoError(t, err)
	assert.NotNil(t, found.UsedAt)
	_, err = tokens.FindByHash(t.Context(), []byte("unknown"))
	require.ErrorIs(t, err, aerrors.ErrNotFound)
}

func TestRefreshToken_RevokeFamily(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	u := createUser(t, d, repository.NewUser(d.Reader), "apple-sub")
	tokens := repository.NewRefreshToken(d.Reader)
	revoked := uuid.NewV7()
	kept := uuid.NewV7()
	inTx(t, d, func(tx tx.Tx) {
		for i, family := range []uuid.UUID{revoked, revoked, kept} {
			require.NoError(t, tokens.Bind(tx).Create(t.Context(), model.RefreshToken{
				UserID:    u.ID,
				FamilyID:  family,
				ExpiresAt: now.Add(time.Hour),
			}, []byte{byte(i)}))
		}
	})

	// act
	inTx(t, d, func(tx tx.Tx) {
		require.NoError(t, tokens.Bind(tx).RevokeFamily(t.Context(), revoked))
	})

	// assert
	for _, hash := range [][]byte{{0}, {1}} {
		_, err := tokens.FindByHash(t.Context(), hash)
		require.ErrorIs(t, err, aerrors.ErrNotFound)
	}
	_, err := tokens.FindByHash(t.Context(), []byte{2})
	require.NoError(t, err)
}
