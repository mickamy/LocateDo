package worker_test

import (
	"context"
	"sync/atomic"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"

	"github.com/mickamy/LocateDo/internal/worker"
)

func TestScheduler_Run_repeatsUntilCancelled(t *testing.T) {
	t.Parallel()

	// arrange
	var runs atomic.Int32
	task := worker.Task{
		Name:     "counting",
		Interval: 10 * time.Millisecond,
		Run: func(context.Context) error {
			runs.Add(1)
			return nil
		},
	}
	ctx, cancel := context.WithTimeout(t.Context(), 100*time.Millisecond)
	defer cancel()

	// act
	worker.NewScheduler(worker.Tasks{task}).Run(ctx)

	// assert
	assert.GreaterOrEqual(t, runs.Load(), int32(3), "once at start, then on the interval")
}
