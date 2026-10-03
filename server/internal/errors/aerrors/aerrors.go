package aerrors

import (
	"errors"
	"fmt"
)

var (
	ErrNotFound     = errors.New("not found")
	ErrConflict     = errors.New("already exists")
	ErrPrecondition = errors.New("precondition failed")

	ErrUnauthenticated = errors.New("unauthenticated")
)

func NotFound(entity string) error {
	return fmt.Errorf("%s: %w", entity, ErrNotFound)
}

func Conflict(detail string) error {
	return fmt.Errorf("%s: %w", detail, ErrConflict)
}

func Precondition(detail string) error {
	return fmt.Errorf("%s: %w", detail, ErrPrecondition)
}

func Unauthenticated(detail string) error {
	return fmt.Errorf("%s: %w", detail, ErrUnauthenticated)
}
