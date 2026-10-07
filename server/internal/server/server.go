package server

import (
	"net/http"
	"time"

	"github.com/mickamy/LocateDo/internal/gen/locatedo/account/v1/accountv1connect"
	"github.com/mickamy/LocateDo/internal/gen/locatedo/category/v1/categoryv1connect"
	"github.com/mickamy/LocateDo/internal/gen/locatedo/device/v1/devicev1connect"
	"github.com/mickamy/LocateDo/internal/gen/locatedo/household/v1/householdv1connect"
	"github.com/mickamy/LocateDo/internal/gen/locatedo/place/v1/placev1connect"
	"github.com/mickamy/LocateDo/internal/gen/locatedo/sync/v1/syncv1connect"
	"github.com/mickamy/LocateDo/internal/gen/locatedo/todo/v1/todov1connect"
)

func Handler(handlers Handlers) http.Handler {
	opt := handlers.Interceptors.Option()

	mux := http.NewServeMux()

	mux.Handle("GET /healthz", handlers.Health)
	mux.Handle("POST /webhooks/revenuecat", handlers.RevenueCat)
	mux.Handle("POST /auth/apple/android", handlers.AppleAndroid)

	mux.Handle(accountv1connect.NewAccountServiceHandler(handlers.Account, opt))
	mux.Handle(householdv1connect.NewHouseholdServiceHandler(handlers.Household, opt))
	mux.Handle(categoryv1connect.NewCategoryServiceHandler(handlers.Category, opt))
	mux.Handle(placev1connect.NewPlaceServiceHandler(handlers.Place, opt))
	mux.Handle(todov1connect.NewTodoServiceHandler(handlers.Todo, opt))
	mux.Handle(devicev1connect.NewDeviceServiceHandler(handlers.Device, opt))
	mux.Handle(syncv1connect.NewSyncServiceHandler(handlers.Sync, opt))

	return allowCORS(mux, handlers.CORS.AllowedOrigins,
		accountv1connect.AccountServiceDeleteAccountWithAppleProcedure,
		accountv1connect.AccountServiceDeleteAccountWithGoogleProcedure,
	)
}

func New(addr string, handlers Handlers) *http.Server {
	protocols := new(http.Protocols)
	protocols.SetHTTP1(true)
	protocols.SetUnencryptedHTTP2(true)

	return &http.Server{
		Addr:              addr,
		Handler:           Handler(handlers),
		ReadHeaderTimeout: 10 * time.Second,
		Protocols:         protocols,
	}
}
