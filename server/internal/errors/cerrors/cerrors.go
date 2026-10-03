package cerrors

import (
	"errors"

	"connectrpc.com/connect"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
)

func Map(err error) *connect.Error {
	switch {
	case errors.Is(err, aerrors.ErrConflict):
		return connect.NewError(connect.CodeAlreadyExists, err)
	case errors.Is(err, aerrors.ErrNotFound):
		return connect.NewError(connect.CodeNotFound, err)
	case errors.Is(err, aerrors.ErrPrecondition):
		return connect.NewError(connect.CodeFailedPrecondition, err)
	case errors.Is(err, aerrors.ErrUnauthenticated):
		return connect.NewError(connect.CodeUnauthenticated, err)
	case errors.Is(err, aerrors.ErrPermissionDenied):
		return connect.NewError(connect.CodePermissionDenied, err)
	case errors.Is(err, aerrors.ErrInvalidArgument):
		return connect.NewError(connect.CodeInvalidArgument, err)
	default:
		return connect.NewError(connect.CodeInternal, err)
	}
}
