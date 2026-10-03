package config

import (
	"context"

	"github.com/caarlos0/env/v11"

	"github.com/mickamy/LocateDo/internal/lib/validator"
)

func parse[T any]() T {
	var config T
	if err := env.Parse(&config); err != nil {
		panic(err)
	}
	if err := validator.Struct(context.Background(), &config); err != nil {
		panic(err)
	}
	return config
}
