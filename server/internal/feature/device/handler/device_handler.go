package handler

import (
	"context"
	"uuid"

	"connectrpc.com/connect"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/errors/cerrors"
	"github.com/mickamy/LocateDo/internal/feature/device/mapper"
	"github.com/mickamy/LocateDo/internal/feature/device/usecase"
	devicev1 "github.com/mickamy/LocateDo/internal/gen/locatedo/device/v1"
	"github.com/mickamy/LocateDo/internal/gen/locatedo/device/v1/devicev1connect"
	"github.com/mickamy/LocateDo/internal/lib/caller"
)

type Device struct {
	_              di.Infra                `di:"embed"`
	registerDevice *usecase.RegisterDevice `di:""`
}

var _ devicev1connect.DeviceServiceHandler = (*Device)(nil)

func (h *Device) RegisterDevice(
	ctx context.Context,
	req *connect.Request[devicev1.RegisterDeviceRequest],
) (*connect.Response[devicev1.RegisterDeviceResponse], error) {
	var userID *uuid.UUID
	if id, err := caller.UserID(ctx); err == nil {
		userID = &id
	}

	if err := h.registerDevice.Do(ctx, usecase.RegisterDeviceInput{
		UserID: userID,
		Device: mapper.DeviceFromRegisterDeviceRequest(req.Msg),
	}); err != nil {
		return nil, cerrors.Map(err)
	}
	return connect.NewResponse(&devicev1.RegisterDeviceResponse{}), nil
}
