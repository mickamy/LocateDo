package main

import (
	"context"
	"fmt"
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
		logger.Error(context.Background(), "worker exited with error", "error", err)
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

	logger.Info(ctx, "worker running")
	worker.NewWorker(infra, di.MustNewLib(cfg)).Run(ctx)
	return nil
}
