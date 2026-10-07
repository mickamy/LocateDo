package model

import (
	"time"
	"uuid"

	"github.com/mickamy/LocateDo/internal/infra/apple"
)

type Provider string

const (
	ProviderApple  Provider = "apple"
	ProviderGoogle Provider = "google"
)

type User struct {
	ID          uuid.UUID
	DisplayName string
	CreatedAt   time.Time
}

type Session struct {
	UserID               uuid.UUID
	AccessToken          string
	AccessTokenExpiresAt time.Time
	RefreshToken         string
	NewUser              bool
}

type RefreshToken struct {
	ID        uuid.UUID
	UserID    uuid.UUID
	FamilyID  uuid.UUID
	ExpiresAt time.Time
	UsedAt    *time.Time
}

// AppleToken is Apple's refresh token, sealed, with the client that obtained
// it and so has to revoke it.
type AppleToken struct {
	Sealed []byte
	Client apple.ClientKind
}

// AppleRevocation is what the worker needs to revoke a deleted user's Apple
// token once the user row, and the token with it, are gone.
type AppleRevocation struct {
	UserID      uuid.UUID        `json:"user_id"`
	SealedToken []byte           `json:"sealed_token"`
	Client      apple.ClientKind `json:"client"`
}
