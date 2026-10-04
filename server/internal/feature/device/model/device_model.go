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

// APNsEnvironment is empty on Android.
type APNsEnvironment string

const (
	APNsSandbox    APNsEnvironment = "sandbox"
	APNsProduction APNsEnvironment = "production"
)

type Device struct {
	ID              uuid.UUID
	UserID          uuid.UUID
	Platform        Platform        `fake:"{randomstring:[ios,android]}"`
	PushToken       string          `fake:"{uuid}"`
	APNsEnvironment APNsEnvironment `fake:"{randomstring:[sandbox,production]}" map:"ApnsEnvironment"`
	LastSeenAt      time.Time
}
