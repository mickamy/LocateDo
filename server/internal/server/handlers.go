package server

import (
	"github.com/mickamy/LocateDo/internal/di"
	account "github.com/mickamy/LocateDo/internal/feature/account/handler"
	category "github.com/mickamy/LocateDo/internal/feature/category/handler"
	device "github.com/mickamy/LocateDo/internal/feature/device/handler"
	household "github.com/mickamy/LocateDo/internal/feature/household/handler"
	place "github.com/mickamy/LocateDo/internal/feature/place/handler"
	sync "github.com/mickamy/LocateDo/internal/feature/sync/handler"
	todo "github.com/mickamy/LocateDo/internal/feature/todo/handler"
	"github.com/mickamy/LocateDo/internal/server/health"
	"github.com/mickamy/LocateDo/internal/server/interceptor"
	"github.com/mickamy/LocateDo/internal/server/webhook"
)

type Handlers struct {
	_            di.Config                `di:"embed"`
	_            di.Infra                 `di:"embed"`
	_            di.Lib                   `di:"embed"`
	Interceptors interceptor.Interceptors `di:""`
	Health       health.Health            `di:""`
	RevenueCat   *webhook.RevenueCat      `di:""`
	Account      *account.Account         `di:""`
	Household    *household.Household     `di:""`
	Category     *category.Category       `di:""`
	Place        *place.Place             `di:""`
	Todo         *todo.Todo               `di:""`
	Device       *device.Device           `di:""`
	Sync         *sync.Sync               `di:""`
}
