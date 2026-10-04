package worker_test

import (
	"context"
	"sync/atomic"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"

	"github.com/mickamy/LocateDo/internal/worker"
)

type countingTask struct {
	runs atomic.Int32
}

func (*countingTask) Name() string            { return "counting" }
func (*countingTask) Interval() time.Duration { return 10 * time.Millisecond }
func (t *countingTask) Run(context.Context) error {
	t.runs.Add(1)
	return nil
}

func TestScheduler_Run_repeatsUntilCancelled(t *testing.T) {
	t.Parallel()

	// arrange
	task := &countingTask{}
	ctx, cancel := context.WithTimeout(t.Context(), 100*time.Millisecond)
	defer cancel()

	// act
	worker.NewScheduler(worker.Tasks{task}).Run(ctx)

	// assert
	assert.GreaterOrEqual(t, task.runs.Load(), int32(3), "once at start, then on the interval")
}
