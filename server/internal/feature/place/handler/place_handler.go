package handler

import "github.com/mickamy/LocateDo/internal/gen/locatedo/place/v1/placev1connect"

type Place struct {
	placev1connect.UnimplementedPlaceServiceHandler
}

var _ placev1connect.PlaceServiceHandler = (*Place)(nil)

func NewPlace() *Place {
	return &Place{}
}
