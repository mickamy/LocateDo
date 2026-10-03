package repository_test

import (
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/account/model"
	"github.com/mickamy/LocateDo/internal/feature/account/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/test/tdb"
)

var now = time.Date(2026, 10, 3, 12, 0, 0, 0, time.UTC)

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

func TestRefreshToken_useOnce(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	u := createUser(t, d, repository.NewUser(d.Reader), "apple-sub")
	tokens := repository.NewRefreshToken(d.Reader)
	familyID := uuid.Must(uuid.NewV7())
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
	revoked := uuid.Must(uuid.NewV7())
	kept := uuid.Must(uuid.NewV7())
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

func TestAppleToken_saveAndFind(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	u := createUser(t, d, repository.NewUser(d.Reader), "apple-sub")
	apple := repository.NewAppleToken(d.Reader)

	// act
	inTx(t, d, func(tx tx.Tx) {
		require.NoError(t, apple.Bind(tx).Save(t.Context(), u.ID, []byte("sealed-1")))
		require.NoError(t, apple.Bind(tx).Save(t.Context(), u.ID, []byte("sealed-2")))
	})

	// assert
	got, err := apple.Find(t.Context(), u.ID)
	require.NoError(t, err)
	assert.Equal(t, []byte("sealed-2"), got)

	_, err = apple.Find(t.Context(), uuid.Must(uuid.NewV7()))
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
			FamilyID:  uuid.Must(uuid.NewV7()),
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

func createUser(t *testing.T, d tdb.DB, users repository.User, subject string) model.User {
	t.Helper()

	var u model.User
	inTx(t, d, func(tx tx.Tx) {
		var err error
		u, err = users.Bind(tx).Create(t.Context(), "")
		require.NoError(t, err)
		require.NoError(t, users.Bind(tx).AddIdentity(t.Context(), u.ID, model.ProviderApple, subject))
	})
	return u
}

func inTx(t *testing.T, d tdb.DB, fn func(tx tx.Tx)) {
	t.Helper()

	require.NoError(t, d.Transactor.WithTx(t.Context(), func(tx tx.Tx) error {
		fn(tx)
		return nil
	}))
}

func inTxErr(t *testing.T, d tdb.DB, fn func(tx tx.Tx) error) {
	t.Helper()

	_ = d.Transactor.WithTx(t.Context(), fn)
}
