package auth_test

import (
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"

	"github.com/mickamy/LocateDo/config"
	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/server/auth"
)

const redirect = "com.locatedo.locatedo://auth/apple"

func TestAppleAndroid_ServeHTTP(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		form url.Values
		want string
	}{
		{
			name: "with name",
			form: url.Values{
				"code":     {"c0de"},
				"id_token": {"eyJ.payload.sig"},
				"state":    {"st/ate+1"},
				//nolint:gosmopolitan // a Japanese family name
				"user": {`{"name":{"firstName":"Mary Ann","lastName":"山田"},"email":"a@example.com"}`},
			},
			want: redirect + "#code=c0de&id_token=eyJ.payload.sig&state=st%2Fate%2B1" +
				"&given_name=Mary%20Ann&family_name=%E5%B1%B1%E7%94%B0",
		},
		{
			name: "without name",
			form: url.Values{"code": {"c0de"}, "id_token": {"tok"}, "state": {"s"}},
			want: redirect + "#code=c0de&id_token=tok&state=s",
		},
		{
			name: "malformed user",
			form: url.Values{"code": {"c0de"}, "id_token": {"tok"}, "state": {"s"}, "user": {"{"}},
			want: redirect + "#code=c0de&id_token=tok&state=s",
		},
		{
			name: "canceled",
			form: url.Values{"error": {"user_cancelled_authorize"}, "state": {"s"}},
			want: redirect + "#state=s&error=user_cancelled_authorize",
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			// arrange
			h := newAppleAndroid(redirect)
			rec := httptest.NewRecorder()

			// act
			h.ServeHTTP(rec, postForm(t, tt.form))

			// assert
			assert.Equal(t, http.StatusFound, rec.Code)
			assert.Equal(t, tt.want, rec.Header().Get("Location"))
			assert.Equal(t, "no-store", rec.Header().Get("Cache-Control"))
		})
	}
}

func TestAppleAndroid_ServeHTTP_ignoresTheQuery(t *testing.T) {
	t.Parallel()

	// arrange
	h := newAppleAndroid(redirect)
	rec := httptest.NewRecorder()
	req := postForm(t, url.Values{"state": {"s"}})
	req.URL.RawQuery = "state=injected&code=injected"

	// act
	h.ServeHTTP(rec, req)

	// assert
	assert.Equal(t, redirect+"#state=s", rec.Header().Get("Location"))
}

func TestAppleAndroid_ServeHTTP_tooLong(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		form url.Values
	}{
		{name: "state", form: url.Values{"state": {strings.Repeat("s", 129)}}},
		{name: "code", form: url.Values{"code": {strings.Repeat("c", 1025)}}},
		{name: "id_token", form: url.Values{"id_token": {strings.Repeat("t", 8193)}}},
		{name: "error", form: url.Values{"error": {strings.Repeat("e", 65)}}},
		{name: "name", form: url.Values{"user": {`{"name":{"firstName":"` + strings.Repeat("x", 101) + `"}}`}}},
		{name: "body", form: url.Values{"padding": {strings.Repeat("p", 16<<10)}}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			// arrange
			h := newAppleAndroid(redirect)
			rec := httptest.NewRecorder()

			// act
			h.ServeHTTP(rec, postForm(t, tt.form))

			// assert
			assert.Equal(t, http.StatusBadRequest, rec.Code)
			assert.Empty(t, rec.Header().Get("Location"))
		})
	}
}

func TestAppleAndroid_ServeHTTP_notConfigured(t *testing.T) {
	t.Parallel()

	// arrange
	h := newAppleAndroid("")
	rec := httptest.NewRecorder()

	// act
	h.ServeHTTP(rec, postForm(t, url.Values{"code": {"c0de"}, "state": {"s"}}))

	// assert
	assert.Equal(t, http.StatusNotFound, rec.Code)
	assert.Empty(t, rec.Header().Get("Location"))
}

func newAppleAndroid(redirect string) *auth.AppleAndroid {
	return auth.NewAppleAndroid(di.Config{Apple: config.Apple{AndroidRedirect: redirect}})
}

func postForm(t *testing.T, form url.Values) *http.Request {
	t.Helper()

	req := httptest.NewRequestWithContext(t.Context(), http.MethodPost, "/auth/apple/android",
		strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	return req
}
