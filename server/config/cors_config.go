package config

// CORS lists the origins of the web pages allowed to call the procedures
// opened to browsers.
type CORS struct {
	AllowedOrigins []string `env:"CORS_ALLOWED_ORIGINS" envSeparator:","`
}

func ParseCORS() CORS {
	return parse[CORS]()
}
