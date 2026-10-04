package config

// APNs is optional in development: without a private key, pushes are logged
// and dropped. One deployment pushes to one app build, so the topic (bundle id)
// and the APNs environment are fixed here.
type APNs struct {
	KeyID       string `env:"APNS_KEY_ID"`
	PrivateKey  string `env:"APNS_PRIVATE_KEY"`
	Topic       string `env:"APNS_TOPIC"       envDefault:"com.locatedo.LocateDo"`
	Environment string `env:"APNS_ENVIRONMENT" envDefault:"production"            validate:"oneof=production sandbox"`
}

func ParseAPNs() APNs {
	return parse[APNs]()
}
