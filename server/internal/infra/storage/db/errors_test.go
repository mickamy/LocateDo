package db_test

import (
	"errors"
	"fmt"
	"testing"

	"github.com/jackc/pgx/v5/pgconn"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/infra/storage/db"
)

func TestIsUnavailable(t *testing.T) {
	t.Parallel()

	// Nothing listens on port 1, so this is a real connection failure.
	_, connectErr := pgconn.Connect(t.Context(), "postgres://admin:password@127.0.0.1:1/locatedo?connect_timeout=1")
	require.Error(t, connectErr)

	tests := map[string]struct {
		err  error
		want bool
	}{
		"connection refused":   {err: fmt.Errorf("begin: %w", connectErr), want: true},
		"server shutting down": {err: &pgconn.PgError{Code: "57P01"}, want: true},
		"connection failure":   {err: &pgconn.PgError{Code: "08006"}, want: true},
		"unique violation":     {err: &pgconn.PgError{Code: "23505"}, want: false},
		"anything else":        {err: errors.New("boom"), want: false},
		"nil":                  {err: nil, want: false},
	}
	for name, tt := range tests {
		t.Run(name, func(t *testing.T) {
			t.Parallel()

			assert.Equal(t, tt.want, db.IsUnavailable(tt.err))
		})
	}
}
