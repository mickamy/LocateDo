package mapper

import (
	"github.com/go-kanna/kanna/mapper"

	categoryv1 "github.com/mickamy/LocateDo/internal/gen/locatedo/category/v1"
)

func init() {
	mapper.Register(BuiltinCategoryToKey)
}

var builtinKeys = map[categoryv1.BuiltinCategory]string{
	categoryv1.BuiltinCategory_BUILTIN_CATEGORY_SHOPPING: "shopping",
	categoryv1.BuiltinCategory_BUILTIN_CATEGORY_WORK:     "work",
	categoryv1.BuiltinCategory_BUILTIN_CATEGORY_LIFE:     "life",
	categoryv1.BuiltinCategory_BUILTIN_CATEGORY_OTHER:    "other",
}

// BuiltinCategoryToKey returns nil for a user-created category.
func BuiltinCategoryToKey(b categoryv1.BuiltinCategory) *string {
	key, ok := builtinKeys[b]
	if !ok {
		return nil
	}
	return &key
}
