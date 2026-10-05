package cerrors_test

import (
	"errors"
	"fmt"
	"testing"

	"connectrpc.com/connect"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/stretchr/testify/assert"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/errors/cerrors"
)

func TestMap(t *testing.T) {
	t.Parallel()

	tests := map[string]struct {
		err  error
		want connect.Code
	}{
		"unauthenticated":      {err: aerrors.Unauthenticated("no"), want: connect.CodeUnauthenticated},
		"database unavailable": {err: fmt.Errorf("find: %w", &pgconn.PgError{Code: "57P03"}), want: connect.CodeUnavailable},
		"anything else":        {err: errors.New("boom"), want: connect.CodeInternal},
	}
	for name, tt := range tests {
		t.Run(name, func(t *testing.T) {
			t.Parallel()

			assert.Equal(t, tt.want, cerrors.Map(tt.err).Code())
		})
	}
}
