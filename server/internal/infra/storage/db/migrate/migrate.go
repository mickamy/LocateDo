package migrate

import (
	"context"
	"crypto/sha256"
	"database/sql"
	"embed"
	"encoding/hex"
	"errors"
	"fmt"
	"io/fs"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/jackc/pgx/v5/stdlib"
	"github.com/pressly/goose/v3"
	"github.com/pressly/goose/v3/database"
)

//go:embed sql/*.sql
var migrations embed.FS

func Up(ctx context.Context, pool *pgxpool.Pool) (err error) {
	sqlDB := stdlib.OpenDBFromPool(pool)
	defer func() {
		err = errors.Join(err, sqlDB.Close())
	}()

	err = UpDB(ctx, sqlDB)
	return err
}

func UpDB(ctx context.Context, sqlDB *sql.DB) (err error) {
	fsys, err := fs.Sub(migrations, "sql")
	if err != nil {
		return fmt.Errorf("sub filesystem: %w", err)
	}
	provider, err := goose.NewProvider(database.DialectPostgres, sqlDB, fsys)
	if err != nil {
		return fmt.Errorf("create goose provider: %w", err)
	}
	defer func(provider *goose.Provider) {
		err = errors.Join(err, provider.Close())
	}(provider)
	if _, err := provider.Up(ctx); err != nil {
		return fmt.Errorf("apply migrations: %w", err)
	}
	return nil
}

func Hash() (string, error) {
	entries, err := fs.ReadDir(migrations, "sql")
	if err != nil {
		return "", fmt.Errorf("read migrations: %w", err)
	}
	h := sha256.New()
	for _, entry := range entries {
		content, err := migrations.ReadFile("sql/" + entry.Name())
		if err != nil {
			return "", fmt.Errorf("read %s: %w", entry.Name(), err)
		}
		_, _ = h.Write([]byte(entry.Name()))
		_, _ = h.Write(content)
	}
	return hex.EncodeToString(h.Sum(nil)), nil
}
