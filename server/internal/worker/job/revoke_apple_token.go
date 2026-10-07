package job

import (
	"context"
	"encoding/json"
	"fmt"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/feature/account/model"
	"github.com/mickamy/LocateDo/internal/infra/apple"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/internal/lib/logger"
	"github.com/mickamy/LocateDo/internal/lib/seal"
	"github.com/mickamy/LocateDo/internal/outbox"
)

// RevokeAppleToken delivers outbox.KindRevokeAppleToken: it opens the sealed
// token the deleted user left behind and revokes it at Apple.
type RevokeAppleToken struct {
	_     di.Infra   `di:"embed"`
	_     di.Lib     `di:"embed"`
	apple apple.Auth `di:""`
	box   seal.Box   `di:""`
}

var _ outbox.Handler = RevokeAppleToken{}

func (h RevokeAppleToken) Handle(ctx context.Context, m outbox.Message) error {
	var r model.AppleRevocation
	if err := json.Unmarshal(m.Payload, &r); err != nil {
		return fmt.Errorf("decode revocation: %w", err)
	}
	refreshToken, err := h.box.Open(r.SealedToken, r.UserID[:])
	if err != nil {
		return fmt.Errorf("open apple token: %w", err)
	}
	if err := h.apple.Revoke(ctx, r.Client, string(refreshToken), clock.Now(ctx)); err != nil {
		return fmt.Errorf("revoke apple token: %w", err)
	}
	logger.Info(ctx, "revoked apple token", "user_id", r.UserID)
	return nil
}
