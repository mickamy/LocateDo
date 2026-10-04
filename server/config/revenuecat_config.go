package config

// RevenueCat is optional in development: without a webhook secret the
// webhook refuses every call, and without API access plans stay as they are.
// The API key is a v2 secret key with read access to customer information;
// the entitlement is its id (entl…), since v2 does not return lookup keys.
type RevenueCat struct {
	BaseURL       string `env:"REVENUECAT_BASE_URL"       envDefault:"https://api.revenuecat.com"`
	APIKey        string `env:"REVENUECAT_API_KEY"`
	ProjectID     string `env:"REVENUECAT_PROJECT_ID"`
	EntitlementID string `env:"REVENUECAT_ENTITLEMENT_ID"`
	WebhookAuth   string `env:"REVENUECAT_WEBHOOK_AUTH"`
}

func ParseRevenueCat() RevenueCat {
	return parse[RevenueCat]()
}
