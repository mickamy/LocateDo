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
	ServicesID string `env:"APPLE_SERVICES_ID"`
	TeamID     string `env:"APPLE_TEAM_ID"`
	KeyID      string `env:"APPLE_KEY_ID"`
	PrivateKey string `env:"APPLE_PRIVATE_KEY"`
}

func ParseApple() Apple {
	return parse[Apple]()
}

// Google is optional in development: without a client id, Google sign-in is
// refused. The client id is the web client of the Firebase project, which is
// what the Android app asks Credential Manager for and what the id token's
// audience names.
type Google struct {
	BaseURL  string `env:"GOOGLE_BASE_URL"  envDefault:"https://www.googleapis.com"`
	ClientID string `env:"GOOGLE_CLIENT_ID"`
}

func ParseGoogle() Google {
	return parse[Google]()
}
