package handler

import "github.com/mickamy/LocateDo/internal/gen/locatedo/household/v1/householdv1connect"

type Household struct {
	householdv1connect.UnimplementedHouseholdServiceHandler
}

var _ householdv1connect.HouseholdServiceHandler = (*Household)(nil)

func NewHousehold() *Household {
	return &Household{}
}
