package config

type Auth struct {
	JWTSigningKey string `env:"JWT_SIGNING_KEY" validate:"required,min=32"`
	// Base64 of 32 bytes; seals secrets such as Apple refresh tokens.
	SealKey string `env:"SEAL_KEY" validate:"required"`
}

func ParseAuth() Auth {
	return parse[Auth]()
}

// Apple is optional in development: without a private key, sign-in fails at
// the code exchange instead of at startup.
type Apple struct {
	BaseURL    string `env:"APPLE_BASE_URL"    envDefault:"https://appleid.apple.com"`
	BundleID   string `env:"APPLE_BUNDLE_ID"   envDefault:"com.locatedo.LocateDo"`
	TeamID     string `env:"APPLE_TEAM_ID"`
	KeyID      string `env:"APPLE_KEY_ID"`
	PrivateKey string `env:"APPLE_PRIVATE_KEY"`
}

func ParseApple() Apple {
	return parse[Apple]()
}
