package worker

import (
	"context"
	"log/slog"
	"sync"
	"time"
)

// Task is time-driven maintenance: it runs once at start-up and then on its
// interval, and must be safe to run again at any time.
type Task interface {
	Name() string
	Interval() time.Duration
	Run(ctx context.Context) error
}

type Tasks []Task

func NewTasks() Tasks {
	return Tasks{}
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
	ticker := time.NewTicker(t.Interval())
	defer ticker.Stop()
	for {
		if err := t.Run(ctx); err != nil {
			slog.ErrorContext(ctx, "task failed", "task", t.Name(), "error", err)
		}
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
		}
	}
}
