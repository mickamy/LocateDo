package main

import (
	"context"
	"fmt"
	"log/slog"
	"os"
	"os/signal"
	"syscall"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/lib/logger"
	"github.com/mickamy/LocateDo/internal/worker"
)

func main() {
	cfg := di.NewConfig()
	logger.Init(cfg.App.ModuleRoot, cfg.App.LogLevel.String())

	if err := run(cfg); err != nil {
		slog.Error("worker exited with error", "error", err)
		os.Exit(1)
	}
}

func run(cfg di.Config) error {
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	infra, err := di.NewInfra(ctx, cfg)
	if err != nil {
		return fmt.Errorf("build infrastructure: %w", err)
	}
	defer func() {
		_ = infra.Close()
	}()

	slog.Info("worker running")
	worker.NewWorker(infra).Run(ctx)
	return nil
}
