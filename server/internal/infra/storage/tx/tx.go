package tx

import (
	"context"
	"fmt"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/mickamy/LocateDo/internal/infra/storage/db"
)

type DBTX interface {
	Exec(ctx context.Context, sql string, args ...any) (pgconn.CommandTag, error)
	Query(ctx context.Context, sql string, args ...any) (pgx.Rows, error)
	QueryRow(ctx context.Context, sql string, args ...any) pgx.Row
}

type Tx struct {
	tx pgx.Tx
}

func (t Tx) DBTX() DBTX { return t.tx }

type Transactor interface {
	WithTx(ctx context.Context, fn func(tx Tx) error) error
}

type transactor struct {
	writer db.Writer
}

var _ Transactor = transactor{}

func NewTransactor(writer db.Writer) Transactor {
	return transactor{writer: writer}
}

func (t transactor) WithTx(ctx context.Context, fn func(tx Tx) error) error {
	pgxTx, err := t.writer.Begin(ctx)
	if err != nil {
		return fmt.Errorf("begin: %w", err)
	}
	return run(ctx, pgxTx, fn)
}

type ReadTransactor interface {
	WithReadTx(ctx context.Context, fn func(tx Tx) error) error
}

type readTransactor struct {
	reader db.Reader
}

var _ ReadTransactor = readTransactor{}

func NewReadTransactor(reader db.Reader) ReadTransactor {
	return readTransactor{reader: reader}
}

func (t readTransactor) WithReadTx(ctx context.Context, fn func(tx Tx) error) error {
	pgxTx, err := t.reader.BeginTx(ctx, pgx.TxOptions{AccessMode: pgx.ReadOnly})
	if err != nil {
		return fmt.Errorf("begin read-only: %w", err)
	}
	return run(ctx, pgxTx, fn)
}

func run(ctx context.Context, pgxTx pgx.Tx, fn func(tx Tx) error) error {
	defer func() {
		_ = pgxTx.Rollback(context.WithoutCancel(ctx))
	}()

	if err := fn(Tx{tx: pgxTx}); err != nil {
		return err
	}
	if err := pgxTx.Commit(ctx); err != nil {
		return fmt.Errorf("commit: %w", err)
	}
	return nil
}
