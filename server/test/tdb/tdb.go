package tdb

import (
	"context"
	"database/sql"
	"fmt"
	"net/url"
	"os"
	"strings"
	"testing"

	_ "github.com/jackc/pgx/v5/stdlib"
	"github.com/peterldowns/pgtestdb"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/config"
	"github.com/mickamy/LocateDo/internal/infra/storage/db"
	"github.com/mickamy/LocateDo/internal/infra/storage/db/migrate"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
)

// DB is a set of typed pools connected to a freshly migrated database.
type DB struct {
	Writer     db.Writer
	Reader     db.Reader
	Transactor tx.Transactor
}

// New provisions an isolated database and returns typed pools connected to
// it. Everything is cleaned up with the test.
func New(t *testing.T) DB {
	t.Helper()

	requireDatabaseEnv(t)
	dbCfg := config.ParseDatabase()

	cfg := pgtestdb.Custom(t, configFromURL(t, dbCfg.AdminURL), migrator{})

	writer, err := db.NewWriter(t.Context(), replaceDBName(t, dbCfg.WriterURL, cfg.Database))
	require.NoError(t, err)
	t.Cleanup(writer.Close)

	reader, err := db.NewReader(t.Context(), replaceDBName(t, dbCfg.ReaderURL, cfg.Database))
	require.NoError(t, err)
	t.Cleanup(reader.Close)

	return DB{Writer: writer, Reader: reader, Transactor: tx.NewTransactor(writer)}
}

func requireDatabaseEnv(t *testing.T) {
	t.Helper()

	var missing []string
	for _, key := range []string{"DATABASE_URL", "DATABASE_WRITER_URL", "DATABASE_READER_URL"} {
		if os.Getenv(key) == "" {
			missing = append(missing, key)
		}
	}
	if len(missing) > 0 {
		t.Fatalf(
			"missing env vars %v: create .env via `envsubst < .env.example > .env` and run tests through make",
			missing,
		)
	}
}

type migrator struct{}

var _ pgtestdb.Migrator = migrator{}

func (migrator) Hash() (string, error) {
	hash, err := migrate.Hash()
	if err != nil {
		return "", fmt.Errorf("hash migrations: %w", err)
	}
	return hash, nil
}

func (migrator) Migrate(ctx context.Context, sqlDB *sql.DB, _ pgtestdb.Config) error {
	if err := migrate.UpDB(ctx, sqlDB); err != nil {
		return fmt.Errorf("apply migrations: %w", err)
	}
	return nil
}

func configFromURL(t *testing.T, raw string) pgtestdb.Config {
	t.Helper()

	u, err := url.Parse(raw)
	require.NoError(t, err)
	password, _ := u.User.Password()
	return pgtestdb.Config{
		DriverName: "pgx",
		Host:       u.Hostname(),
		Port:       u.Port(),
		User:       u.User.Username(),
		Password:   password,
		Database:   strings.TrimPrefix(u.Path, "/"),
		Options:    u.RawQuery,
	}
}

func replaceDBName(t *testing.T, raw, name string) string {
	t.Helper()

	u, err := url.Parse(raw)
	require.NoError(t, err)
	u.Path = "/" + name
	return u.String()
}
