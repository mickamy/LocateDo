package auth

import (
	"encoding/json"
	"net/http"
	"net/url"
	"strings"
	"unicode/utf8"

	"github.com/mickamy/LocateDo/config"
	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/lib/logger"
)

const (
	maxBodyBytes  = 16 << 10
	maxCodeLen    = 1 << 10
	maxIDTokenLen = 8 << 10
	maxStateLen   = 128
	maxErrorLen   = 64
	maxNameLen    = 100
)

// AppleAndroid takes the form Apple posts after web sign-in on Android and hands
// the values to the app through its custom scheme, in the fragment so they
// stay out of logs and referrers on the way. Nothing is verified or kept here:
// the app checks the state, and SignInWithApple verifies the token.
type AppleAndroid struct {
	_   di.Config    `di:"embed"`
	cfg config.Apple `di:""`
}

var _ http.Handler = AppleAndroid{}

type field struct {
	key   string
	value string
}

type appleUser struct {
	Name struct {
		FirstName string `json:"firstName"` //nolint:tagliatelle // Apple's field name
		LastName  string `json:"lastName"`  //nolint:tagliatelle // Apple's field name
	} `json:"name"`
}

func (h AppleAndroid) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()
	if h.cfg.AndroidRedirect == "" {
		http.NotFound(w, r)
		return
	}

	r.Body = http.MaxBytesReader(w, r.Body, maxBodyBytes)
	if err := r.ParseForm(); err != nil {
		logger.Info(ctx, "apple android callback", "result", "malformed")
		http.Error(w, "malformed form", http.StatusBadRequest)
		return
	}
	fields, ok := fragmentFields(r.PostForm)
	if !ok {
		logger.Info(ctx, "apple android callback", "result", "too long")
		http.Error(w, "value too long", http.StatusBadRequest)
		return
	}

	result := "forwarded"
	if r.PostForm.Get("error") != "" {
		result = "error"
	}
	logger.Info(ctx, "apple android callback", "result", result)

	w.Header().Set("Location", h.cfg.AndroidRedirect+"#"+encode(fields))
	w.Header().Set("Cache-Control", "no-store")
	w.Header().Set("Referrer-Policy", "no-referrer")
	w.WriteHeader(http.StatusFound)
}

func fragmentFields(form url.Values) ([]field, bool) {
	limits := []struct {
		key string
		max int
	}{
		{key: "code", max: maxCodeLen},
		{key: "id_token", max: maxIDTokenLen},
		{key: "state", max: maxStateLen},
		{key: "error", max: maxErrorLen},
	}

	var fields []field
	for _, l := range limits {
		v := form.Get(l.key)
		if len(v) > l.max {
			return nil, false
		}
		fields = append(fields, field{key: l.key, value: v})
	}

	var user appleUser
	if err := json.Unmarshal([]byte(form.Get("user")), &user); err != nil {
		return fields, true
	}
	if utf8.RuneCountInString(user.Name.FirstName) > maxNameLen ||
		utf8.RuneCountInString(user.Name.LastName) > maxNameLen {
		return nil, false
	}
	fields = append(fields,
		field{key: "given_name", value: user.Name.FirstName},
		field{key: "family_name", value: user.Name.LastName},
	)
	return fields, true
}

// encode escapes spaces as %20 rather than +, so the app can percent-decode
// the fragment as is.
func encode(fields []field) string {
	var parts []string
	for _, f := range fields {
		if f.value == "" {
			continue
		}
		parts = append(parts, f.key+"="+strings.ReplaceAll(url.QueryEscape(f.value), "+", "%20"))
	}
	return strings.Join(parts, "&")
}
