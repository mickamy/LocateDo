package config

type Database struct {
	AdminURL  string `env:"DATABASE_URL"        validate:"required"`
	WriterURL string `env:"DATABASE_WRITER_URL" validate:"required"`
	ReaderURL string `env:"DATABASE_READER_URL" validate:"required"`
}

func ParseDatabase() Database {
	return parse[Database]()
}
