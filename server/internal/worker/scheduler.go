package worker

import (
	"context"
	"sync"
	"time"

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

const sweepInterval = time.Hour

func NewTasks(
	tombstones *job.SweepTombstones,
	refreshTokens *job.SweepRefreshTokens,
	deadMessages *job.SweepDeadMessages,
) Tasks {
	return Tasks{
		{Name: "sweep tombstones", Interval: sweepInterval, Run: tombstones.Run},
		{Name: "sweep refresh tokens", Interval: sweepInterval, Run: refreshTokens.Run},
		{Name: "sweep dead messages", Interval: sweepInterval, Run: deadMessages.Run},
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
		if err := t.Run(ctx); err != nil {
			logger.Error(ctx, "task failed", "task", t.Name, "error", err)
		}
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
		}
	}
}
