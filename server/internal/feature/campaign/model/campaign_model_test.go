package model_test

import (
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/campaign/model"
)

func TestCampaign_Validate(t *testing.T) {
	t.Parallel()

	valid := model.Campaign{Language: "ja", Title: "New", Body: "Repeating to-dos are here"}
	tests := []struct {
		name   string
		modify func(c *model.Campaign)
		ok     bool
	}{
		{name: "valid", modify: func(*model.Campaign) {}, ok: true},
		{name: "https URL", modify: func(c *model.Campaign) { c.URL = "https://locatedo.com/news" }, ok: true},
		{name: "title at the limit in runes", modify: func(c *model.Campaign) { c.Title = kana(50) }, ok: true},
		{name: "body at the limit in runes", modify: func(c *model.Campaign) { c.Body = kana(200) }, ok: true},
		{name: "unknown language", modify: func(c *model.Campaign) { c.Language = "fr" }},
		{name: "empty title", modify: func(c *model.Campaign) { c.Title = "" }},
		{name: "long title", modify: func(c *model.Campaign) { c.Title = kana(51) }},
		{name: "empty body", modify: func(c *model.Campaign) { c.Body = "" }},
		{name: "long body", modify: func(c *model.Campaign) { c.Body = strings.Repeat("a", 201) }},
		{name: "http URL", modify: func(c *model.Campaign) { c.URL = "http://locatedo.com" }},
		{name: "URL without a host", modify: func(c *model.Campaign) { c.URL = "https:///news" }},
		{name: "custom scheme", modify: func(c *model.Campaign) { c.URL = "locatedo://paywall" }},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			// arrange
			c := valid
			tt.modify(&c)

			// act
			err := c.Validate()

			// assert
			if tt.ok {
				assert.NoError(t, err)
				return
			}
			require.ErrorIs(t, err, aerrors.ErrInvalidArgument)
		})
	}
}

func kana(n int) string {
	return strings.Repeat("あ", n)
}
