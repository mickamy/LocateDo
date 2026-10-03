package interceptor

import (
	"connectrpc.com/connect"
	"connectrpc.com/validate"
	"github.com/mickamy/LocateDo/internal/di"
)

func NewInterceptors(cfg di.Config) []connect.Interceptor {
	return []connect.Interceptor{
		Recovery(),
		Logging(cfg.App),
		validate.NewInterceptor(),
		Clock(),
	}
}

func Option(cfg di.Config) connect.Option {
	return connect.WithInterceptors(NewInterceptors(cfg)...)
}
