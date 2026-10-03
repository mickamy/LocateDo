package tinfra

import (
	"testing"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/test/tdb"
)

func New(t *testing.T) di.Infra {
	t.Helper()

	d := tdb.New(t)
	return di.Infra{
		Writer:         d.Writer,
		Reader:         d.Reader,
		Transactor:     d.Transactor,
		ReadTransactor: tx.NewReadTransactor(d.Reader),
	}
}
