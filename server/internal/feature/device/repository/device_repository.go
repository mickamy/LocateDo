package repository

import (
	"context"
	"errors"
	"fmt"
	"time"
	"uuid"

	"github.com/jackc/pgx/v5"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/device/model"
	"github.com/mickamy/LocateDo/internal/feature/device/queries"
	"github.com/mickamy/LocateDo/internal/infra/storage/db"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
)

type Device interface {
	FindByTokenForUpdate(ctx context.Context, platform model.Platform, token string) (model.Device, error)
	// Upsert registers the token for the device's user, taking it over from
	// whoever held it before. A device without a user keeps the owner the
	// token already has.
	Upsert(ctx context.Context, d model.Device) (uuid.UUID, error)
	// ListByHousehold lists every member's devices on the platform.
	ListByHousehold(ctx context.Context, householdID uuid.UUID, platform model.Platform) ([]model.Device, error)
	Delete(ctx context.Context, id uuid.UUID) error
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

func (r device) FindByTokenForUpdate(
	ctx context.Context,
	platform model.Platform,
	token string,
) (model.Device, error) {
	row, err := r.q.FindDeviceByTokenForUpdate(ctx, queries.FindDeviceByTokenForUpdateParams{
		Platform:  string(platform),
		PushToken: token,
	})
	if errors.Is(err, pgx.ErrNoRows) {
		return model.Device{}, aerrors.NotFound("device")
	}
	if err != nil {
		return model.Device{}, fmt.Errorf("find device: %w", err)
	}
	return toModel(row), nil
}

func (r device) Upsert(ctx context.Context, d model.Device) (uuid.UUID, error) {
	var env *string
	if d.APNsEnvironment != "" {
		v := string(d.APNsEnvironment)
		env = &v
	}
	var consentedAt *time.Time
	if d.PromotionsConsent {
		consentedAt = &d.LastSeenAt
	}
	id, err := r.q.UpsertDevice(ctx, queries.UpsertDeviceParams{
		UserID:                d.UserID,
		Platform:              string(d.Platform),
		PushToken:             d.PushToken,
		ApnsEnvironment:       env,
		Language:              string(d.Language),
		PromotionsConsentedAt: consentedAt,
		LastSeenAt:            d.LastSeenAt,
	})
	switch {
	case db.IsForeignKeyViolation(err):
		return uuid.UUID{}, aerrors.InvalidArgument("device refers to an unknown user")
	case err != nil:
		return uuid.UUID{}, fmt.Errorf("upsert device: %w", err)
	}
	return id, nil
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
		return nil, fmt.Errorf("list household devices: %w", err)
	}
	devices := make([]model.Device, 0, len(rows))
	for _, row := range rows {
		devices = append(devices, toModel(row))
	}
	return devices, nil
}

func (r device) Delete(ctx context.Context, id uuid.UUID) error {
	if err := r.q.DeleteDevice(ctx, id); err != nil {
		return fmt.Errorf("delete device: %w", err)
	}
	return nil
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
		UserID:    &userID,
		Platform:  string(platform),
		PushToken: token,
	}); err != nil {
		return fmt.Errorf("delete owned device: %w", err)
	}
	return nil
}

func toModel(row queries.Device) model.Device {
	var env model.APNsEnvironment
	if row.ApnsEnvironment != nil {
		env = model.APNsEnvironment(*row.ApnsEnvironment)
	}
	return model.Device{
		ID:                row.ID,
		UserID:            row.UserID,
		Platform:          model.Platform(row.Platform),
		PushToken:         row.PushToken,
		APNsEnvironment:   env,
		Language:          model.Language(row.Language),
		PromotionsConsent: row.PromotionsConsentedAt != nil,
		LastSeenAt:        row.LastSeenAt,
	}
}
