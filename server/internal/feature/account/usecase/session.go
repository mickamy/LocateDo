package usecase

import (
	"context"
	"fmt"
	"time"
	"uuid"

	"github.com/mickamy/LocateDo/internal/feature/account/model"
	"github.com/mickamy/LocateDo/internal/feature/account/repository"
	"github.com/mickamy/LocateDo/internal/lib/token"
)

// startSession stores a refresh token in familyID and issues the access token
// that goes with it. Call it inside the caller's transaction.
func startSession(
	ctx context.Context,
	tokens repository.RefreshToken,
	signer token.Signer,
	userID, familyID uuid.UUID,
	now time.Time,
) (model.Session, error) {
	raw, hash := token.NewOpaque()
	err := tokens.Create(ctx, model.RefreshToken{
		UserID:    userID,
		FamilyID:  familyID,
		ExpiresAt: now.Add(token.RefreshTTL),
	}, hash)
	if err != nil {
		return model.Session{}, fmt.Errorf("store refresh token: %w", err)
	}

	access, expiresAt, err := signer.IssueAccess(userID, now)
	if err != nil {
		return model.Session{}, fmt.Errorf("issue access token: %w", err)
	}

	return model.Session{
		UserID:               userID,
		AccessToken:          access,
		AccessTokenExpiresAt: expiresAt,
		RefreshToken:         raw,
	}, nil
}
