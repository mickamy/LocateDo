package handler

import (
	"context"

	"connectrpc.com/connect"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/errors/cerrors"
	"github.com/mickamy/LocateDo/internal/feature/place/mapper"
	"github.com/mickamy/LocateDo/internal/feature/place/usecase"
	placev1 "github.com/mickamy/LocateDo/internal/gen/locatedo/place/v1"
	"github.com/mickamy/LocateDo/internal/gen/locatedo/place/v1/placev1connect"
	"github.com/mickamy/LocateDo/internal/lib/caller"
	"github.com/mickamy/LocateDo/internal/lib/ids"
)

type Place struct {
	_           di.Infra             `di:"embed"`
	putPlace    *usecase.PutPlace    `di:""`
	deletePlace *usecase.DeletePlace `di:""`
}

var _ placev1connect.PlaceServiceHandler = (*Place)(nil)

func (h *Place) PutPlace(
	ctx context.Context,
	req *connect.Request[placev1.PutPlaceRequest],
) (*connect.Response[placev1.PutPlaceResponse], error) {
	userID, err := caller.UserID(ctx)
	if err != nil {
		return nil, cerrors.Map(err)
	}
	householdID, err := ids.Parse("household_id", req.Msg.GetHouseholdId())
	if err != nil {
		return nil, cerrors.Map(err)
	}
	p, err := mapper.PlaceFromPlaceInput(req.Msg.GetPlace())
	if err != nil {
		return nil, cerrors.Map(aerrors.InvalidArgument(err.Error()))
	}
	p.HouseholdID = householdID

	if err := h.putPlace.Do(ctx, usecase.PutPlaceInput{UserID: userID, Place: p}); err != nil {
		return nil, cerrors.Map(err)
	}
	return connect.NewResponse(&placev1.PutPlaceResponse{}), nil
}

func (h *Place) DeletePlace(
	ctx context.Context,
	req *connect.Request[placev1.DeletePlaceRequest],
) (*connect.Response[placev1.DeletePlaceResponse], error) {
	userID, err := caller.UserID(ctx)
	if err != nil {
		return nil, cerrors.Map(err)
	}
	id, err := ids.Parse("id", req.Msg.GetId())
	if err != nil {
		return nil, cerrors.Map(err)
	}

	if err := h.deletePlace.Do(ctx, usecase.DeletePlaceInput{UserID: userID, PlaceID: id}); err != nil {
		return nil, cerrors.Map(err)
	}
	return connect.NewResponse(&placev1.DeletePlaceResponse{}), nil
}
