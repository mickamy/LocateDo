package repository

import (
	"context"
	"fmt"

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
	err := r.q.UpsertDevice(ctx, queries.UpsertDeviceParams{
		UserID:     d.UserID,
		Platform:   string(d.Platform),
		PushToken:  d.PushToken,
		LastSeenAt: d.LastSeenAt,
	})
	switch {
	case db.IsForeignKeyViolation(err):
		return aerrors.InvalidArgument("device refers to an unknown user")
	case err != nil:
		return fmt.Errorf("upsert device: %w", err)
	}
	return nil
}
