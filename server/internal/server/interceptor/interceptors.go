package interceptor

import (
	"connectrpc.com/connect"
	"connectrpc.com/validate"

	"github.com/mickamy/LocateDo/config"
	"github.com/mickamy/LocateDo/internal/di"
	hrepository "github.com/mickamy/LocateDo/internal/feature/household/repository"
	"github.com/mickamy/LocateDo/internal/lib/token"
)

//kanna:container returns=Interceptors
type Interceptors struct {
	_           di.Config              `di:"embed"`
	_           di.Infra               `di:"embed"`
	_           di.Lib                 `di:"embed"`
	app         config.App             `di:""`
	signer      token.Signer           `di:""`
	memberships hrepository.Membership `di:""`
}

func (i Interceptors) Option() connect.Option {
	return connect.WithInterceptors(
		Recovery(),
		Logging(i.app),
		Clock(),
		Auth(i.signer),
		validate.NewInterceptor(),
		Household(i.memberships),
	)
}
