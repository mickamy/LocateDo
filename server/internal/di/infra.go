package di

import (
	"context"
	"fmt"
	"net/http"
	"time"

	"github.com/mickamy/LocateDo/config"
	"github.com/mickamy/LocateDo/internal/infra/apple"
	"github.com/mickamy/LocateDo/internal/infra/storage/db"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
)

const appleHTTPTimeout = 10 * time.Second

//kanna:container must returns=Infra
type Infra struct {
	_              context.Context   `di:"arg"` //nolint:containedctx // required by kanna-di
	_              Config            `di:"embed"`
	Writer         db.Writer         `di:"with=provideWriter"`
	Reader         db.Reader         `di:"with=provideReader"`
	Transactor     tx.Transactor     `di:"with=provideTransactor"`
	ReadTransactor tx.ReadTransactor `di:"with=provideReadTransactor"`
	Apple          apple.Auth        `di:"with=provideApple"`
}

func (infra *Infra) Close() error {
	infra.Writer.Close()
	infra.Reader.Close()
	return nil
}

func provideWriter(ctx context.Context, cfg config.Database) (db.Writer, error) {
	writer, err := db.NewWriter(ctx, cfg.WriterURL)
	if err != nil {
		return db.Writer{}, fmt.Errorf("new writer: %w", err)
	}
	return writer, nil
}

func provideReader(ctx context.Context, cfg config.Database, writer db.Writer) (db.Reader, error) {
	reader, err := db.NewReader(ctx, cfg.ReaderURL)
	if err != nil {
		writer.Close()
		return db.Reader{}, fmt.Errorf("new reader: %w", err)
	}
	return reader, nil
}

func provideTransactor(writer db.Writer) tx.Transactor {
	return tx.NewTransactor(writer)
}

func provideReadTransactor(reader db.Reader) tx.ReadTransactor {
	return tx.NewReadTransactor(reader)
}

func provideApple(cfg config.Apple) (apple.Auth, error) {
	appleCfg := apple.Config{
		BaseURL:  cfg.BaseURL,
		BundleID: cfg.BundleID,
		TeamID:   cfg.TeamID,
		KeyID:    cfg.KeyID,
	}
	if cfg.PrivateKey != "" {
		key, err := apple.ParsePrivateKey([]byte(cfg.PrivateKey))
		if err != nil {
			return nil, fmt.Errorf("parse APPLE_PRIVATE_KEY: %w", err)
		}
		appleCfg.PrivateKey = key
	}
	return apple.NewClient(appleCfg, &http.Client{Timeout: appleHTTPTimeout}), nil
}
