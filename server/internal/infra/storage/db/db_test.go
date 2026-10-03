package db_test

import (
	"testing"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/mickamy/LocateDo/config"
	"github.com/mickamy/LocateDo/internal/infra/storage/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestNew(t *testing.T) {
	t.Parallel()

	// arrange
	url := config.ParseDatabase().AdminURL

	// act
	pool, err := db.New(t.Context(), url)

	// assert
	require.NoError(t, err)
	pool.Close()
}

func TestNewWriter(t *testing.T) {
	t.Parallel()

	// arrange
	url := config.ParseDatabase().WriterURL

	// act
	w, err := db.NewWriter(t.Context(), url)
	require.NoError(t, err)
	defer w.Close()

	// assert
	assertCurrentUser(t, w.Pool, "locatedo_writer")
}

func TestNewReader(t *testing.T) {
	t.Parallel()

	// arrange
	url := config.ParseDatabase().ReaderURL

	// act
	r, err := db.NewReader(t.Context(), url)
	require.NoError(t, err)
	defer r.Close()

	// assert
	assertCurrentUser(t, r.Pool, "locatedo_reader")
}

func assertCurrentUser(t *testing.T, pool *pgxpool.Pool, want string) {
	t.Helper()

	var got string
	err := pool.QueryRow(t.Context(), "SELECT current_user").Scan(&got)
	require.NoError(t, err)
	assert.Equal(t, want, got)
}
