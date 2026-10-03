package config

import "fmt"

type Env string

const (
	EnvDevelopment Env = "development"
	EnvTest        Env = "test"
	EnvStaging     Env = "staging"
	EnvProduction  Env = "production"
)

func (e Env) String() string {
	return string(e)
}

func (e Env) ShortName() string {
	switch e {
	case EnvDevelopment:
		return "dev"
	case EnvTest:
		return "test"
	case EnvStaging:
		return "stg"
	case EnvProduction:
		return "prod"
	}

	panic(fmt.Sprintf("unknown environment: %s", e))
}

func (e Env) IsDevelopment() bool {
	return e == EnvDevelopment
}

func (e Env) IsTest() bool {
	return e == EnvTest
}

func (e Env) IsStaging() bool {
	return e == EnvStaging
}

func (e Env) IsProduction() bool {
	return e == EnvProduction
}

type LogLevel string

const (
	LogLevelDebug LogLevel = "debug"
	LogLevelInfo  LogLevel = "info"
	LogLevelWarn  LogLevel = "warn"
	LogLevelError LogLevel = "error"
)

type App struct {
	Env        Env      `env:"ENV"         envDefault:"development"`
	LogLevel   LogLevel `env:"LOG_LEVEL"`
	ModuleRoot string   `env:"MODULE_ROOT" validate:"required"`
}

func ParseApp() App {
	return parse[App]()
}
