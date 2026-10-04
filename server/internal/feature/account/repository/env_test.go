package repository_test

import (
	"testing"
	"time"

	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/feature/account/model"
	"github.com/mickamy/LocateDo/internal/feature/account/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/test/tdb"
)

var now = time.Date(2026, 10, 3, 12, 0, 0, 0, time.UTC)

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
