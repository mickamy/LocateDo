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

type Language string

const (
	LanguageEnglish  Language = "en"
	LanguageJapanese Language = "ja"
)

type Device struct {
	ID uuid.UUID
	// UserID is nil for a device registered without signing in.
	UserID            *uuid.UUID
	Platform          Platform        `fake:"{randomstring:[ios,android]}"`
	PushToken         string          `fake:"{uuid}"`
	APNsEnvironment   APNsEnvironment `fake:"{randomstring:[sandbox,production]}" map:"ApnsEnvironment"`
	Language          Language        `fake:"{randomstring:[en,ja]}"`
	PromotionsConsent bool
	LastSeenAt        time.Time
}

type PromotionsConsentChange struct {
	DeviceID uuid.UUID
	// UserID is the device's owner at the time, nil for an anonymous device.
	UserID    *uuid.UUID
	Consented bool
	ChangedAt time.Time
}
