package interceptor

import (
	"connectrpc.com/connect"
	"connectrpc.com/validate"

	"github.com/mickamy/LocateDo/internal/di"
)

func NewInterceptors(cfg di.Config, lib di.Lib) []connect.Interceptor {
	return []connect.Interceptor{
		Recovery(),
		Logging(cfg.App),
		Clock(),
		Auth(lib.Signer),
		validate.NewInterceptor(),
	}
}

func Option(cfg di.Config, lib di.Lib) connect.Option {
	return connect.WithInterceptors(NewInterceptors(cfg, lib)...)
}
