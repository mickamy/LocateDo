package interceptor

import (
	"context"
	"runtime"

	"connectrpc.com/connect"

	"github.com/mickamy/LocateDo/internal/lib/logger"
)

func Recovery() connect.UnaryInterceptorFunc {
	return func(next connect.UnaryFunc) connect.UnaryFunc {
		return func(
			ctx context.Context,
			req connect.AnyRequest,
		) (res connect.AnyResponse, err error) {
			defer func() {
				if r := recover(); r != nil {
					buf := make([]byte, 4096)
					n := runtime.Stack(buf, false)
					logger.Error(ctx, "panic recovered",
						"panic", r,
						"procedure", req.Spec().Procedure,
						"stack", string(buf[:n]),
					)
					res = nil
					err = connect.NewError(connect.CodeInternal, errInternal)
				}
			}()
			return next(ctx, req)
		}
	}
}
