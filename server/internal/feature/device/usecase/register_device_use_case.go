package usecase

import (
	"context"
	"errors"
	"fmt"
	"uuid"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/device/model"
	"github.com/mickamy/LocateDo/internal/feature/device/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/internal/lib/logger"
)

type RegisterDeviceInput struct {
	// UserID is nil when the device registers without signing in.
	UserID *uuid.UUID
	Device model.Device
}

type RegisterDevice struct {
	_              di.Infra                           `di:"embed"`
	transactor     tx.Transactor                      `di:""`
	devices        repository.Device                  `di:""`
	consentChanges repository.PromotionsConsentChange `di:""`
}

// Do registers a signed-in caller's device as theirs. An anonymous caller only
// keeps a row while consenting to promotions: withdrawing consent deletes an
// anonymous row, and a row with an owner keeps it.
func (uc RegisterDevice) Do(ctx context.Context, in RegisterDeviceInput) error {
	d := in.Device
	d.UserID = in.UserID
	d.LastSeenAt = clock.Now(ctx)

	if err := uc.transactor.WithTx(ctx, func(tx tx.Tx) error {
		devices := uc.devices.Bind(tx)
		consentChanges := uc.consentChanges.Bind(tx)

		current, err := devices.FindByTokenForUpdate(ctx, d.Platform, d.PushToken)
		found := err == nil
		if err != nil && !errors.Is(err, aerrors.ErrNotFound) {
			return fmt.Errorf("find device: %w", err)
		}
		owner := in.UserID
		if owner == nil {
			owner = current.UserID
		}

		if in.UserID == nil && !d.PromotionsConsent {
			if !found {
				return nil
			}
			if current.UserID == nil {
				if err := devices.Delete(ctx, current.ID); err != nil {
					return fmt.Errorf("delete device: %w", err)
				}
				return recordConsentChange(ctx, consentChanges, current, d, current.ID, owner)
			}
		}

		id, err := devices.Upsert(ctx, d)
		if err != nil {
			return fmt.Errorf("upsert device: %w", err)
		}
		return recordConsentChange(ctx, consentChanges, current, d, id, owner)
	}); err != nil {
		return fmt.Errorf("register device: %w", err)
	}
	return nil
}

func recordConsentChange(
	ctx context.Context,
	consentChanges repository.PromotionsConsentChange,
	before, after model.Device,
	id uuid.UUID,
	owner *uuid.UUID,
) error {
	if before.PromotionsConsent == after.PromotionsConsent {
		return nil
	}
	if err := consentChanges.Insert(ctx, model.PromotionsConsentChange{
		DeviceID:  id,
		UserID:    owner,
		Consented: after.PromotionsConsent,
		ChangedAt: after.LastSeenAt,
	}); err != nil {
		return fmt.Errorf("record consent change: %w", err)
	}
	logger.Info(ctx, "promotions consent changed", "device_id", id, "platform", after.Platform,
		"to", after.PromotionsConsent, "anonymous", owner == nil)
	return nil
}
