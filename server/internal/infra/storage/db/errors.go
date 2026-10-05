package db

import (
	"errors"
	"strings"

	"github.com/jackc/pgx/v5/pgconn"
)

const (
	uniqueViolationCode     = "23505"
	foreignKeyViolationCode = "23503"
)

// IsUniqueViolation reports whether err is a unique-constraint violation.
func IsUniqueViolation(err error) bool {
	pgErr, ok := errors.AsType[*pgconn.PgError](err)
	return ok && pgErr.Code == uniqueViolationCode
}

// IsForeignKeyViolation reports whether err is a foreign-key violation.
func IsForeignKeyViolation(err error) bool {
	pgErr, ok := errors.AsType[*pgconn.PgError](err)
	return ok && pgErr.Code == foreignKeyViolationCode
}

// IsUnavailable reports whether err means the database could not be reached
// or is not accepting work right now (connection failures, shutdown, startup),
// as opposed to a problem with the request.
func IsUnavailable(err error) bool {
	if _, ok := errors.AsType[*pgconn.ConnectError](err); ok {
		return true
	}
	pgErr, ok := errors.AsType[*pgconn.PgError](err)
	return ok && (strings.HasPrefix(pgErr.Code, "08") || strings.HasPrefix(pgErr.Code, "57P"))
}
