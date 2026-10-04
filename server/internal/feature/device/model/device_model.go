package model

import (
	"time"
	"uuid"
)

type Platform string

const (
	PlatformIOS     Platform = "ios"
	PlatformAndroid Platform = "android"
)

type Device struct {
	ID         uuid.UUID
	UserID     uuid.UUID
	Platform   Platform `fake:"{randomstring:[ios,android]}"`
	PushToken  string   `fake:"{uuid}"`
	LastSeenAt time.Time
}
