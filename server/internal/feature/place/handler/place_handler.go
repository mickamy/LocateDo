package handler

import (
	"context"
	"fmt"
	"uuid"

	"connectrpc.com/connect"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/errors/cerrors"
	"github.com/mickamy/LocateDo/internal/feature/place/mapper"
	"github.com/mickamy/LocateDo/internal/feature/place/usecase"
	placev1 "github.com/mickamy/LocateDo/internal/gen/locatedo/place/v1"
	"github.com/mickamy/LocateDo/internal/gen/locatedo/place/v1/placev1connect"
	"github.com/mickamy/LocateDo/internal/lib/caller"
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
	userID, err := callerID(ctx)
	if err != nil {
		return nil, cerrors.Map(err)
	}
	householdID, err := parseID("household_id", req.Msg.GetHouseholdId())
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
	userID, err := callerID(ctx)
	if err != nil {
		return nil, cerrors.Map(err)
	}
	id, err := parseID("id", req.Msg.GetId())
	if err != nil {
		return nil, cerrors.Map(err)
	}

	if err := h.deletePlace.Do(ctx, usecase.DeletePlaceInput{UserID: userID, PlaceID: id}); err != nil {
		return nil, cerrors.Map(err)
	}
	return connect.NewResponse(&placev1.DeletePlaceResponse{}), nil
}

func callerID(ctx context.Context) (uuid.UUID, error) {
	id, ok := caller.UserID(ctx)
	if !ok {
		return uuid.UUID{}, aerrors.Unauthenticated("no caller")
	}
	return id, nil
}

func parseID(field, raw string) (uuid.UUID, error) {
	id, err := uuid.Parse(raw)
	if err != nil {
		return uuid.UUID{}, aerrors.InvalidArgument(fmt.Sprintf("%s: %v", field, err))
	}
	return id, nil
}
