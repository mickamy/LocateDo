package server

import (
	"net/http"
	"time"

	"github.com/mickamy/LocateDo/internal/di"
)

func Handler(cfg di.Config, handlers Handlers) http.Handler {
	mux := http.NewServeMux()

	return mux
}

func New(addr string, cfg di.Config, handlers Handlers) *http.Server {
	protocols := new(http.Protocols)
	protocols.SetHTTP1(true)
	protocols.SetUnencryptedHTTP2(true)

	return &http.Server{
		Addr:              addr,
		Handler:           Handler(cfg, handlers),
		ReadHeaderTimeout: 10 * time.Second,
		Protocols:         protocols,
	}
}
