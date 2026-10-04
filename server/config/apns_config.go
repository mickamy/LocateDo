package config

// APNs is optional in development: without a private key, pushes are logged
// and dropped. One deployment pushes to one app build, so the topic (bundle id)
// is fixed here; the APNs environment comes from each device.
type APNs struct {
	KeyID      string `env:"APNS_KEY_ID"`
	PrivateKey string `env:"APNS_PRIVATE_KEY"`
	Topic      string `env:"APNS_TOPIC"       envDefault:"com.locatedo.LocateDo"`
}

func ParseAPNs() APNs {
	return parse[APNs]()
}
