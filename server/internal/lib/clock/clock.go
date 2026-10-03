package clock

import (
	"context"
	"sync"
	"time"
)

type Clock interface {
	Now() time.Time
}

func New() Clock { return System{} }

type System struct{}

func (System) Now() time.Time { return time.Now().UTC() }

type Fixed struct {
	mu sync.RWMutex
	t  time.Time
}

func NewFixed(t time.Time) *Fixed { return &Fixed{t: t} }

func (f *Fixed) Now() time.Time {
	f.mu.RLock()
	defer f.mu.RUnlock()
	return f.t
}

func (f *Fixed) Set(t time.Time) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.t = t
}

func (f *Fixed) Advance(d time.Duration) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.t = f.t.Add(d)
}

type clockKey struct{}

func Get(ctx context.Context) Clock {
	c, ok := ctx.Value(clockKey{}).(Clock)
	if !ok {
		return System{}
	}
	return c
}

func Set(ctx context.Context, c Clock) context.Context {
	return context.WithValue(ctx, clockKey{}, c)
}

func Now(ctx context.Context) time.Time {
	return Get(ctx).Now()
}
