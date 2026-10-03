package server

import (
	"github.com/mickamy/LocateDo/internal/di"
)

type Handlers struct {
	_ di.Infra `di:"embed"`
}
