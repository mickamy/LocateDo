package mapper

import (
	"github.com/go-kanna/kanna/mapper"

	"github.com/mickamy/LocateDo/internal/feature/household/model"
	householdv1 "github.com/mickamy/LocateDo/internal/gen/locatedo/household/v1"
)

func init() {
	mapper.Register(PlanToHouseholdv1)
	mapper.Register(RoleToHouseholdv1)
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

func RoleToHouseholdv1(r model.Role) householdv1.Role {
	switch r {
	case model.RoleOwner:
		return householdv1.Role_ROLE_OWNER
	case model.RoleMember:
		return householdv1.Role_ROLE_MEMBER
	default:
		return householdv1.Role_ROLE_UNSPECIFIED
	}
}
