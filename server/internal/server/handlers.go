package server

import (
	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/server/health"
)

type Handlers struct {
	_      di.Infra      `di:"embed"`
	Health health.Health `di:""`
}
