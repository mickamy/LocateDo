package mapper

import (
	"github.com/go-kanna/kanna/mapper"

	"github.com/mickamy/LocateDo/internal/feature/household/model"
	categoryv1 "github.com/mickamy/LocateDo/internal/gen/locatedo/category/v1"
	householdv1 "github.com/mickamy/LocateDo/internal/gen/locatedo/household/v1"
)

func init() {
	mapper.Register(BuiltinCategoryToKey)
	mapper.Register(PlanToHouseholdv1)
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

func PlanToHouseholdv1(p model.Plan) householdv1.Plan {
	switch p {
	case model.PlanFree:
		return householdv1.Plan_PLAN_FREE
	case model.PlanPro:
		return householdv1.Plan_PLAN_PRO
	default:
		return householdv1.Plan_PLAN_UNSPECIFIED
	}
}
