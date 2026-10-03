package interceptor

import (
	"context"

	"connectrpc.com/connect"
	"github.com/mickamy/LocateDo/internal/lib/clock"
)

func Clock() connect.UnaryInterceptorFunc {
	return func(next connect.UnaryFunc) connect.UnaryFunc {
		return func(
			ctx context.Context,
			req connect.AnyRequest,
		) (connect.AnyResponse, error) {
			c := clock.New()
			ctx = clock.Set(ctx, c)

			return next(ctx, req)
		}
	}
}
