package config

// RevenueCat is optional in development: without a webhook secret the
// webhook refuses every call, and without an API key plans stay as they are.
type RevenueCat struct {
	BaseURL     string `env:"REVENUECAT_BASE_URL"     envDefault:"https://api.revenuecat.com"`
	APIKey      string `env:"REVENUECAT_API_KEY"`
	WebhookAuth string `env:"REVENUECAT_WEBHOOK_AUTH"`
	Entitlement string `env:"REVENUECAT_ENTITLEMENT"  envDefault:"pro"`
}

func ParseRevenueCat() RevenueCat {
	return parse[RevenueCat]()
}
