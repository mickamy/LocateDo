package config

// FCM is optional in development: without a service account, Android pushes
// are logged and dropped. The value is the service account's JSON key, as the
// file or as base64 of it.
type FCM struct {
	ServiceAccount string `env:"FCM_SERVICE_ACCOUNT"`
}

func ParseFCM() FCM {
	return parse[FCM]()
}
