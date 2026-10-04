package worker

import (
	"context"
	"sync"

	"github.com/mickamy/LocateDo/internal/di"
)

type Worker struct {
	_         di.Infra  `di:"embed"`
	_         di.Lib    `di:"embed"`
	Consumer  Consumer  `di:""`
	Scheduler Scheduler `di:""`
}

func (w Worker) Run(ctx context.Context) {
	var wg sync.WaitGroup
	wg.Go(func() { w.Consumer.Run(ctx) })
	wg.Go(func() { w.Scheduler.Run(ctx) })
	wg.Wait()
}
