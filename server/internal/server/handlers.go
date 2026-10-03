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
)

type Handlers struct {
	_         di.Infra             `di:"embed"`
	Health    health.Health        `di:""`
	Account   *account.Account     `di:""`
	Household *household.Household `di:""`
	Category  *category.Category   `di:""`
	Place     *place.Place         `di:""`
	Todo      *todo.Todo           `di:""`
	Device    *device.Device       `di:""`
	Sync      *sync.Sync           `di:""`
}
