package logger

import (
	"context"
	"log/slog"
	"os"
	"runtime"
	"strconv"
	"strings"
	"uuid"

	"github.com/mickamy/LocateDo/internal/lib/execution"
)

const callerSkipDepth = 2

var moduleRoot string

func Init(root, level string) {
	slog.SetDefault(slog.New(jsonHandler(level)))
	moduleRoot = root
}

func jsonHandler(level string) *slog.JSONHandler {
	var opts = &slog.HandlerOptions{
		Level: logLevel(level),
	}
	return slog.NewJSONHandler(os.Stdout, opts)
}

func logLevel(level string) slog.Level {
	switch level {
	case "debug":
		return slog.LevelDebug
	case "info":
		return slog.LevelInfo
	case "warn":
		return slog.LevelWarn
	case "error":
		return slog.LevelError
	default:
		return slog.LevelInfo
	}
}

func internalHandle(ctx context.Context, level slog.Level, msg string, args ...any) {
	_, f, l, _ := runtime.Caller(callerSkipDepth)
	source := f + ":" + strconv.Itoa(l)

	source = strings.TrimPrefix(source, moduleRoot+"/")
	args = append(args, slog.String("source", source))

	execID := execution.Get(ctx)
	if execID != uuid.Nil() {
		args = append(args, slog.String("execution_id", execID.String()))
	}
	if name := execution.JobName(ctx); name != "" {
		args = append(args, slog.String("job_name", name))
	}

	slog.Default().Log(ctx, level, msg, args...)
}

func Debug(ctx context.Context, msg string, args ...any) {
	internalHandle(ctx, slog.LevelDebug, msg, args...)
}

func Info(ctx context.Context, msg string, args ...any) {
	internalHandle(ctx, slog.LevelInfo, msg, args...)
}

func Warn(ctx context.Context, msg string, args ...any) {
	internalHandle(ctx, slog.LevelWarn, msg, args...)
}

func Error(ctx context.Context, msg string, args ...any) {
	internalHandle(ctx, slog.LevelError, msg, args...)
}
