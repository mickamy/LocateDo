package handler

import "github.com/mickamy/LocateDo/internal/gen/locatedo/device/v1/devicev1connect"

type Device struct {
	devicev1connect.UnimplementedDeviceServiceHandler
}

var _ devicev1connect.DeviceServiceHandler = (*Device)(nil)

func NewDevice() *Device {
	return &Device{}
}
