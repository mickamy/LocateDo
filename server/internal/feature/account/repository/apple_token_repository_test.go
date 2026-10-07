package repository_test

import (
	"testing"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/account/model"
	"github.com/mickamy/LocateDo/internal/feature/account/repository"
	"github.com/mickamy/LocateDo/internal/infra/apple"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestAppleToken_saveAndFind(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	u := createUser(t, d, repository.NewUser(d.Reader), "apple-sub")
	tokens := repository.NewAppleToken(d.Reader)
	second := model.AppleToken{Sealed: []byte("sealed-2"), Client: apple.ClientServices}

	// act
	d.InTx(t, func(tx tx.Tx) {
		first := model.AppleToken{Sealed: []byte("sealed-1"), Client: apple.ClientApp}
		require.NoError(t, tokens.Bind(tx).Save(t.Context(), u.ID, first))
		require.NoError(t, tokens.Bind(tx).Save(t.Context(), u.ID, second))
	})

	// assert
	got, err := tokens.Find(t.Context(), u.ID)
	require.NoError(t, err)
	assert.Equal(t, second, got)

	_, err = tokens.Find(t.Context(), uuid.NewV7())
	require.ErrorIs(t, err, aerrors.ErrNotFound)
}
