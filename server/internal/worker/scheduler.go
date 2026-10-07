package worker

import (
	"context"
	"sync"
	"time"
	"uuid"

	"github.com/mickamy/LocateDo/internal/lib/execution"
	"github.com/mickamy/LocateDo/internal/lib/logger"
	"github.com/mickamy/LocateDo/internal/worker/job"
)

// Task is time-driven maintenance: it runs once at start-up and then on its
// interval, and must be safe to run again at any time.
type Task struct {
	Name     string
	Interval time.Duration
	Run      func(ctx context.Context) error
}

type Tasks []Task

const (
	sweepInterval     = time.Hour
	reconcileInterval = 24 * time.Hour
)

func NewTasks(
	tombstones *job.SweepTombstones,
	refreshTokens *job.SweepRefreshTokens,
	deadMessages *job.SweepDeadMessages,
	entitlements *job.ReconcileEntitlements,
) Tasks {
	return Tasks{
		{Name: "sweep_tombstones", Interval: sweepInterval, Run: tombstones.Run},
		{Name: "sweep_refresh_tokens", Interval: sweepInterval, Run: refreshTokens.Run},
		{Name: "sweep_dead_messages", Interval: sweepInterval, Run: deadMessages.Run},
		{Name: "reconcile_entitlements", Interval: reconcileInterval, Run: entitlements.Run},
	}
}

type Scheduler struct {
	tasks Tasks
}

func NewScheduler(tasks Tasks) Scheduler {
	return Scheduler{tasks: tasks}
}

// Run drives every task on its own interval until ctx ends.
func (s Scheduler) Run(ctx context.Context) {
	var wg sync.WaitGroup
	for _, t := range s.tasks {
		wg.Go(func() { s.loop(ctx, t) })
	}
	wg.Wait()
}

func (s Scheduler) loop(ctx context.Context, t Task) {
	ticker := time.NewTicker(t.Interval)
	defer ticker.Stop()
	for {
		runCtx := execution.SetID(execution.SetJobName(ctx, t.Name), uuid.NewV7())
		if err := t.Run(runCtx); err != nil {
			logger.Error(runCtx, "task failed", "error", err)
		}
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
		}
	}
}
