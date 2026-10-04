package model

import (
	"time"
	"uuid"
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

// AppleRevocation is what the worker needs to revoke a deleted user's Apple
// token once the user row, and the token with it, are gone.
type AppleRevocation struct {
	UserID      uuid.UUID `json:"user_id"`
	SealedToken []byte    `json:"sealed_token"`
}
