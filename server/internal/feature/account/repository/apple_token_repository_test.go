package repository_test

import (
	"testing"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/account/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestAppleToken_saveAndFind(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	u := createUser(t, d, repository.NewUser(d.Reader), "apple-sub")
	apple := repository.NewAppleToken(d.Reader)

	// act
	d.InTx(t, func(tx tx.Tx) {
		require.NoError(t, apple.Bind(tx).Save(t.Context(), u.ID, []byte("sealed-1")))
		require.NoError(t, apple.Bind(tx).Save(t.Context(), u.ID, []byte("sealed-2")))
	})

	// assert
	got, err := apple.Find(t.Context(), u.ID)
	require.NoError(t, err)
	assert.Equal(t, []byte("sealed-2"), got)

	_, err = apple.Find(t.Context(), uuid.NewV7())
	require.ErrorIs(t, err, aerrors.ErrNotFound)
}
