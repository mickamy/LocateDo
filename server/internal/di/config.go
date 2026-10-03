package di

import "github.com/mickamy/LocateDo/config"

//kanna:container returns=Config
type Config struct {
	App      config.App      `di:""`
	Database config.Database `di:""`
}
