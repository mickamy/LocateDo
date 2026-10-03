package analyze

import "strings"

var pluralCategories = map[string][]string{
	"en": {"one", "other"},
	"ja": {"other"},
}

func requiredCategories(lang string) ([]string, bool) {
	base, _, _ := strings.Cut(lang, "-")
	categories, ok := pluralCategories[base]
	return categories, ok
}
