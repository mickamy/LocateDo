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

func TestUser_createAndFindByIdentity(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	users := repository.NewUser(d.Reader)
	var created model.User
	inTx(t, d, func(tx tx.Tx) {
		var err error
		created, err = users.Bind(tx).Create(t.Context(), "Tetsuro")
		require.NoError(t, err)
		require.NoError(t, users.Bind(tx).AddIdentity(t.Context(), created.ID, model.ProviderApple, "apple-sub"))
	})

	// act
	got, err := users.FindByIdentity(t.Context(), model.ProviderApple, "apple-sub")

	// assert
	require.NoError(t, err)
	assert.Equal(t, created.ID, got.ID)
	assert.Equal(t, "Tetsuro", got.DisplayName)

	_, err = users.FindByIdentity(t.Context(), model.ProviderGoogle, "apple-sub")
	require.ErrorIs(t, err, aerrors.ErrNotFound)
}

func TestUser_AddIdentity_conflict(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	users := repository.NewUser(d.Reader)
	first := createUser(t, d, users, "apple-sub")

	// act
	var err error
	inTxErr(t, d, func(tx tx.Tx) error {
		second, cerr := users.Bind(tx).Create(t.Context(), "")
		require.NoError(t, cerr)
		err = users.Bind(tx).AddIdentity(t.Context(), second.ID, model.ProviderApple, "apple-sub")
		return err
	})

	// assert
	require.ErrorIs(t, err, aerrors.ErrConflict)
	got, ferr := users.FindByIdentity(t.Context(), model.ProviderApple, "apple-sub")
	require.NoError(t, ferr)
	assert.Equal(t, first.ID, got.ID)
}

func TestUser_Delete(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	users := repository.NewUser(d.Reader)
	u := createUser(t, d, users, "apple-sub")

	// act & assert
	inTx(t, d, func(tx tx.Tx) {
		require.NoError(t, users.Bind(tx).Delete(t.Context(), u.ID))
		require.ErrorIs(t, users.Bind(tx).Delete(t.Context(), u.ID), aerrors.ErrNotFound)
	})
	_, err := users.FindByIdentity(t.Context(), model.ProviderApple, "apple-sub")
	require.ErrorIs(t, err, aerrors.ErrNotFound)
}

func TestUser_Delete_cascadesTokens(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	users := repository.NewUser(d.Reader)
	tokens := repository.NewRefreshToken(d.Reader)
	apple := repository.NewAppleToken(d.Reader)
	u := createUser(t, d, users, "apple-sub")
	inTx(t, d, func(tx tx.Tx) {
		require.NoError(t, tokens.Bind(tx).Create(t.Context(), model.RefreshToken{
			UserID:    u.ID,
			FamilyID:  uuid.NewV7(),
			ExpiresAt: now.Add(time.Hour),
		}, []byte("hash")))
		require.NoError(t, apple.Bind(tx).Save(t.Context(), u.ID, []byte("sealed")))
	})

	// act
	inTx(t, d, func(tx tx.Tx) {
		require.NoError(t, users.Bind(tx).Delete(t.Context(), u.ID))
	})

	// assert
	_, err := tokens.FindByHash(t.Context(), []byte("hash"))
	require.ErrorIs(t, err, aerrors.ErrNotFound)
	_, err = apple.Find(t.Context(), u.ID)
	require.ErrorIs(t, err, aerrors.ErrNotFound)
}
