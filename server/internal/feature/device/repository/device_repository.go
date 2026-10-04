package repository

import (
	"context"
	"fmt"
	"uuid"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/device/model"
	"github.com/mickamy/LocateDo/internal/feature/device/queries"
	"github.com/mickamy/LocateDo/internal/infra/storage/db"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
)

type Device interface {
	// Upsert registers the token for the device's user, taking it over from
	// whoever held it before.
	Upsert(ctx context.Context, d model.Device) error
	// ListByHousehold lists every member's devices on the platform.
	ListByHousehold(ctx context.Context, householdID uuid.UUID, platform model.Platform) ([]model.Device, error)
	DeleteByToken(ctx context.Context, platform model.Platform, token string) error
	DeleteOwnedByToken(ctx context.Context, userID uuid.UUID, platform model.Platform, token string) error
	Bind(tx tx.Tx) Device
}

type device struct {
	q *queries.Queries
}

var _ Device = device{}

func NewDevice(reader db.Reader) Device {
	return device{q: queries.New(reader)}
}

func (r device) Bind(tx tx.Tx) Device {
	return device{q: queries.New(tx.DBTX())}
}

func (r device) Upsert(ctx context.Context, d model.Device) error {
	var env *string
	if d.APNsEnvironment != "" {
		v := string(d.APNsEnvironment)
		env = &v
	}
	err := r.q.UpsertDevice(ctx, queries.UpsertDeviceParams{
		UserID:          d.UserID,
		Platform:        string(d.Platform),
		PushToken:       d.PushToken,
		ApnsEnvironment: env,
		LastSeenAt:      d.LastSeenAt,
	})
	switch {
	case db.IsForeignKeyViolation(err):
		return aerrors.InvalidArgument("device refers to an unknown user")
	case err != nil:
		return fmt.Errorf("upsert device: %w", err)
	}
	return nil
}

func (r device) ListByHousehold(
	ctx context.Context,
	householdID uuid.UUID,
	platform model.Platform,
) ([]model.Device, error) {
	rows, err := r.q.ListHouseholdDevices(ctx, queries.ListHouseholdDevicesParams{
		HouseholdID: householdID,
		Platform:    string(platform),
	})
	if err != nil {
		return nil, fmt.Errorf("list devices: %w", err)
	}
	devices := make([]model.Device, 0, len(rows))
	for _, row := range rows {
		var env model.APNsEnvironment
		if row.ApnsEnvironment != nil {
			env = model.APNsEnvironment(*row.ApnsEnvironment)
		}
		devices = append(devices, model.Device{
			ID:              row.ID,
			UserID:          row.UserID,
			Platform:        model.Platform(row.Platform),
			PushToken:       row.PushToken,
			APNsEnvironment: env,
			LastSeenAt:      row.LastSeenAt,
		})
	}
	return devices, nil
}

func (r device) DeleteByToken(ctx context.Context, platform model.Platform, token string) error {
	if err := r.q.DeleteDeviceByToken(ctx, queries.DeleteDeviceByTokenParams{
		Platform:  string(platform),
		PushToken: token,
	}); err != nil {
		return fmt.Errorf("delete device: %w", err)
	}
	return nil
}

func (r device) DeleteOwnedByToken(ctx context.Context, userID uuid.UUID, platform model.Platform, token string) error {
	if err := r.q.DeleteUserDeviceByToken(ctx, queries.DeleteUserDeviceByTokenParams{
		UserID:    userID,
		Platform:  string(platform),
		PushToken: token,
	}); err != nil {
		return fmt.Errorf("delete owned device: %w", err)
	}
	return nil
}
