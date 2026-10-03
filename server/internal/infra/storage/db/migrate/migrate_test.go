package migrate_test

import (
	"context"
	"fmt"
	"net/url"
	"testing"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/config"
	"github.com/mickamy/LocateDo/internal/infra/storage/db"
	"github.com/mickamy/LocateDo/internal/infra/storage/db/migrate"
)

func TestUp(t *testing.T) {
	t.Parallel()

	adminURL := config.ParseDatabase().AdminURL
	admin, err := db.New(t.Context(), adminURL)
	require.NoError(t, err)
	t.Cleanup(admin.Close)

	name := fmt.Sprintf("migrate_test_%d", time.Now().UnixNano())
	ident := pgx.Identifier{name}.Sanitize()
	_, err = admin.Exec(t.Context(), "CREATE DATABASE "+ident)
	require.NoError(t, err)
	t.Cleanup(func() {
		ctx := context.WithoutCancel(t.Context())
		_, err := admin.Exec(ctx, "DROP DATABASE IF EXISTS "+ident+" WITH (FORCE)")
		assert.NoError(t, err)
	})

	pool, err := db.New(t.Context(), replaceDBName(t, adminURL, name))
	require.NoError(t, err)
	t.Cleanup(pool.Close)

	require.NoError(t, migrate.Up(t.Context(), pool))

	var roles int
	row := pool.QueryRow(t.Context(),
		"SELECT count(*) FROM pg_roles WHERE rolname IN ('locatedo_writer', 'locatedo_reader')")
	require.NoError(t, row.Scan(&roles))
	require.Equal(t, 2, roles)

	assert.NoError(t, migrate.Up(t.Context(), pool), "second run must be a no-op")
}

func replaceDBName(t *testing.T, raw, name string) string {
	t.Helper()

	u, err := url.Parse(raw)
	require.NoError(t, err)
	u.Path = "/" + name
	return u.String()
}
