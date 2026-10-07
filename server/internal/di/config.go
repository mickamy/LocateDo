package di

import "github.com/mickamy/LocateDo/config"

//kanna:container returns=Config
type Config struct {
	App        config.App        `di:""`
	Database   config.Database   `di:""`
	Auth       config.Auth       `di:""`
	Apple      config.Apple      `di:""`
	Google     config.Google     `di:""`
	APNs       config.APNs       `di:""`
	FCM        config.FCM        `di:""`
	RevenueCat config.RevenueCat `di:""`
	CORS       config.CORS       `di:""`
}
