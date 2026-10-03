package interceptor

import (
	"context"
	"log/slog"
	"slices"

	"connectrpc.com/connect"
	"github.com/mickamy/LocateDo/config"
	"github.com/mickamy/LocateDo/internal/lib/execution"
	"github.com/mickamy/LocateDo/internal/lib/logger"
)

var sensitiveHeaders = map[string]bool{
	"Authorization": true,
	"Cookie":        true,
	"Set-Cookie":    true,
}

func Logging(cfg config.App) connect.UnaryInterceptorFunc {
	return func(next connect.UnaryFunc) connect.UnaryFunc {
		return func(
			ctx context.Context,
			req connect.AnyRequest,
		) (connect.AnyResponse, error) {
			execID, err := execution.NewID()
			if err != nil {
				logger.Error(ctx, "failed to generate execution ID", "error", err)
				return nil, connect.NewError(connect.CodeInternal, errInternal)
			}
			ctx = execution.Set(ctx, execID)

			// log the request details
			reqFields := []any{slog.String("procedure", req.Spec().Procedure)}
			reqHeader := req.Header()
			for k, val := range reqHeader {
				if sensitiveHeaders[k] {
					reqFields = append(reqFields, slog.String("header."+k, "[REDACTED]"))
				} else {
					reqFields = append(reqFields, slog.Any("header."+k, val))
				}
			}
			shouldLogPayload := shouldLogPayload(cfg)
			if shouldLogPayload {
				reqFields = append(reqFields, slog.Any("payload", req.Any()))
			}
			logger.Debug(ctx, "request", reqFields...)

			// execute the next interceptor or handler
			res, err := next(ctx, req)

			if connect.CodeOf(err) == connect.CodeInternal {
				fields := make([]any, 0, 2+len(reqFields))
				fields = append(fields, "error", err)
				fields = append(fields, reqFields...)
				logger.Error(ctx, "internal error", fields...)
				return res, connect.NewError(connect.CodeInternal, errInternal)
			}

			// log the response details
			resFields := []any{slog.String("procedure", req.Spec().Procedure)}
			if err != nil {
				resFields = append(resFields, slog.String("error", err.Error()))
				logger.Debug(ctx, "response", resFields...)
			} else {
				resHeader := res.Header()
				for k, val := range resHeader {
					if sensitiveHeaders[k] {
						resFields = append(resFields, slog.String("header."+k, "[REDACTED]"))
					} else {
						resFields = append(resFields, slog.Any("header."+k, val))
					}
				}
				if shouldLogPayload {
					resFields = append(resFields, slog.Any("payload", res.Any()))
				}
				logger.Debug(ctx, "response", resFields...)
			}

			return res, err
		}
	}
}

var envExceptProduction = []config.Env{
	config.EnvTest,
	config.EnvDevelopment,
	config.EnvStaging,
}

func shouldLogPayload(cfg config.App) bool {
	return slices.Contains(envExceptProduction, cfg.Env)
}
