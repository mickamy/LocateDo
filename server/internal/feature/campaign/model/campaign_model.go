package model

import (
	"fmt"
	"net/url"
	"time"
	"unicode/utf8"
	"uuid"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	dmodel "github.com/mickamy/LocateDo/internal/feature/device/model"
)

const (
	MaxTitleLength = 50
	MaxBodyLength  = 200
	// Interval is the least time between two campaigns in one language.
	Interval = 14 * 24 * time.Hour
)

type Campaign struct {
	ID       uuid.UUID
	Language dmodel.Language
	Title    string
	Body     string
	// URL is empty when the campaign only opens the app.
	URL         string
	TargetCount int
	CreatedAt   time.Time
	// SentAt is nil until every device has been tried.
	SentAt *time.Time
}

func (c Campaign) Validate() error {
	if c.Language != dmodel.LanguageEnglish && c.Language != dmodel.LanguageJapanese {
		return aerrors.InvalidArgument(fmt.Sprintf("language must be en or ja, got %q", c.Language))
	}
	if n := utf8.RuneCountInString(c.Title); n == 0 || n > MaxTitleLength {
		return aerrors.InvalidArgument(fmt.Sprintf("title must be 1 to %d characters, got %d", MaxTitleLength, n))
	}
	if n := utf8.RuneCountInString(c.Body); n == 0 || n > MaxBodyLength {
		return aerrors.InvalidArgument(fmt.Sprintf("body must be 1 to %d characters, got %d", MaxBodyLength, n))
	}
	if c.URL == "" {
		return nil
	}
	u, err := url.Parse(c.URL)
	if err != nil || u.Scheme != "https" || u.Host == "" {
		return aerrors.InvalidArgument(fmt.Sprintf("url must be an https URL, got %q", c.URL))
	}
	return nil
}

// Audience counts the devices a campaign reaches, by platform.
type Audience struct {
	IOS     int
	Android int
}

func (a Audience) Total() int {
	return a.IOS + a.Android
}
