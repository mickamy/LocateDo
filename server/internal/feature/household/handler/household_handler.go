package handler

import (
	"context"
	"fmt"
	"uuid"

	"connectrpc.com/connect"
	"google.golang.org/protobuf/types/known/timestamppb"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/errors/cerrors"
	"github.com/mickamy/LocateDo/internal/feature/household/mapper"
	"github.com/mickamy/LocateDo/internal/feature/household/usecase"
	householdv1 "github.com/mickamy/LocateDo/internal/gen/locatedo/household/v1"
	"github.com/mickamy/LocateDo/internal/gen/locatedo/household/v1/householdv1connect"
	"github.com/mickamy/LocateDo/internal/lib/caller"
)

type Household struct {
	_               di.Infra                 `di:"embed"`
	createHousehold *usecase.CreateHousehold `di:""`
	createInvite    *usecase.CreateInvite    `di:""`
	acceptInvite    *usecase.AcceptInvite    `di:""`
	removeMember    *usecase.RemoveMember    `di:""`
}

var _ householdv1connect.HouseholdServiceHandler = (*Household)(nil)

func (h *Household) CreateHousehold(
	ctx context.Context,
	req *connect.Request[householdv1.CreateHouseholdRequest],
) (*connect.Response[householdv1.CreateHouseholdResponse], error) {
	userID, err := callerID(ctx)
	if err != nil {
		return nil, cerrors.Map(err)
	}
	householdID, err := parseID("id", req.Msg.GetId())
	if err != nil {
		return nil, cerrors.Map(err)
	}
	contents, err := mapper.ContentsFromCreateHouseholdRequest(req.Msg)
	if err != nil {
		return nil, cerrors.Map(aerrors.InvalidArgument(err.Error()))
	}

	out, err := h.createHousehold.Do(ctx, usecase.CreateHouseholdInput{
		UserID:      userID,
		HouseholdID: householdID,
		Contents:    contents,
	})
	if err != nil {
		return nil, cerrors.Map(err)
	}
	return connect.NewResponse(&householdv1.CreateHouseholdResponse{
		Household: mapper.HouseholdToHouseholdv1(out.Household),
	}), nil
}

func (h *Household) CreateInvite(
	ctx context.Context,
	req *connect.Request[householdv1.CreateInviteRequest],
) (*connect.Response[householdv1.CreateInviteResponse], error) {
	userID, err := callerID(ctx)
	if err != nil {
		return nil, cerrors.Map(err)
	}
	householdID, err := parseID("household_id", req.Msg.GetHouseholdId())
	if err != nil {
		return nil, cerrors.Map(err)
	}

	out, err := h.createInvite.Do(ctx, usecase.CreateInviteInput{UserID: userID, HouseholdID: householdID})
	if err != nil {
		return nil, cerrors.Map(err)
	}
	return connect.NewResponse(&householdv1.CreateInviteResponse{
		Token:     out.Token,
		ExpiresAt: timestamppb.New(out.ExpiresAt),
	}), nil
}

func (h *Household) AcceptInvite(
	ctx context.Context,
	req *connect.Request[householdv1.AcceptInviteRequest],
) (*connect.Response[householdv1.AcceptInviteResponse], error) {
	userID, err := callerID(ctx)
	if err != nil {
		return nil, cerrors.Map(err)
	}

	out, err := h.acceptInvite.Do(ctx, usecase.AcceptInviteInput{UserID: userID, Token: req.Msg.GetToken()})
	if err != nil {
		return nil, cerrors.Map(err)
	}
	return connect.NewResponse(&householdv1.AcceptInviteResponse{
		Household: mapper.HouseholdToHouseholdv1(out.Household),
	}), nil
}

func (h *Household) RemoveMember(
	ctx context.Context,
	req *connect.Request[householdv1.RemoveMemberRequest],
) (*connect.Response[householdv1.RemoveMemberResponse], error) {
	callerUserID, err := callerID(ctx)
	if err != nil {
		return nil, cerrors.Map(err)
	}
	householdID, err := parseID("household_id", req.Msg.GetHouseholdId())
	if err != nil {
		return nil, cerrors.Map(err)
	}
	userID, err := parseID("user_id", req.Msg.GetUserId())
	if err != nil {
		return nil, cerrors.Map(err)
	}

	if err := h.removeMember.Do(ctx, usecase.RemoveMemberInput{
		CallerID:    callerUserID,
		HouseholdID: householdID,
		UserID:      userID,
	}); err != nil {
		return nil, cerrors.Map(err)
	}
	return connect.NewResponse(&householdv1.RemoveMemberResponse{}), nil
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
