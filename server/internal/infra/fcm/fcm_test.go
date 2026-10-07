package fcm_test

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"encoding/pem"
	"io"
	"net/http"
	"net/http/httptest"
	"strconv"
	"sync"
	"testing"
	"time"
	"uuid"

	"github.com/golang-jwt/jwt/v5"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/infra/fcm"
)

const (
	projectID   = "locatedo-stg"
	clientEmail = "firebase-adminsdk@locatedo-stg.iam.gserviceaccount.com"
)

var now = time.Date(2026, 10, 6, 12, 0, 0, 0, time.UTC)

func TestParseServiceAccount(t *testing.T) {
	t.Parallel()

	// arrange
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	require.NoError(t, err)
	file := serviceAccountJSON(t, key)

	// act
	fromJSON, err := fcm.ParseServiceAccount(file)
	require.NoError(t, err)
	fromBase64, err := fcm.ParseServiceAccount(base64.StdEncoding.EncodeToString([]byte(file)))

	// assert
	require.NoError(t, err)
	assert.Equal(t, projectID, fromJSON.ProjectID)
	assert.Equal(t, clientEmail, fromJSON.ClientEmail)
	assert.True(t, key.Equal(fromJSON.PrivateKey))
	assert.Equal(t, fromJSON, fromBase64)
}

func TestParseServiceAccount_rejects(t *testing.T) {
	t.Parallel()

	for name, raw := range map[string]string{
		"not base64":      "%%%",
		"not json":        base64.StdEncoding.EncodeToString([]byte("nope")),
		"missing project": `{"client_email":"a@b","private_key":"x"}`,
		"broken key": `{"project_id":"p","client_email":"a@b",` +
			`"private_key":"-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----\n"}`,
		"no pem in the key": `{"project_id":"p","client_email":"a@b","private_key":"x"}`,
	} {
		t.Run(name, func(t *testing.T) {
			t.Parallel()

			_, err := fcm.ParseServiceAccount(raw)

			require.Error(t, err)
		})
	}
}

func TestClient_Wake(t *testing.T) {
	t.Parallel()

	// arrange
	fake := newFakeFCM(t)
	client := fake.client(t)

	// act
	err := client.Wake(t.Context(), "installation-1", now)

	// assert
	require.NoError(t, err)
	sent := fake.lastMessage()
	assert.Equal(t, "installation-1", sent.Message.Fid)
	assert.Equal(t, map[string]string{"reason": "sync"}, sent.Message.Data)
	assert.Equal(t, "normal", sent.Message.Android.Priority)
	assert.Equal(t, "Bearer access-token-1", fake.lastAuthorization())
	fake.assertAssertion(t)
}

func TestClient_Promote(t *testing.T) {
	t.Parallel()

	campaignID := uuid.MustParse("019a0000-0000-7000-8000-000000000001")
	tests := map[string]struct {
		url  string
		want map[string]string
	}{
		"with a URL": {
			url: "https://locatedo.com/news",
			want: map[string]string{
				"type": "campaign", "campaign_id": campaignID.String(), "title": "New", "body": "Try it",
				"url": "https://locatedo.com/news",
			},
		},
		"without a URL": {
			url: "",
			want: map[string]string{
				"type": "campaign", "campaign_id": campaignID.String(), "title": "New", "body": "Try it",
			},
		},
	}
	for name, tt := range tests {
		t.Run(name, func(t *testing.T) {
			t.Parallel()

			// arrange
			fake := newFakeFCM(t)
			client := fake.client(t)
			p := fcm.Promotion{CampaignID: campaignID, Title: "New", Body: "Try it", URL: tt.url}

			// act
			err := client.Promote(t.Context(), "installation-1", p, now)

			// assert
			require.NoError(t, err)
			sent := fake.lastMessage()
			assert.Equal(t, "installation-1", sent.Message.Fid)
			assert.Equal(t, tt.want, sent.Message.Data)
			assert.Equal(t, "normal", sent.Message.Android.Priority)
			assert.Nil(t, sent.Message.Notification, "the app builds the notification itself")
		})
	}
}

func TestClient_Wake_reusesTheAccessTokenUntilItExpires(t *testing.T) {
	t.Parallel()

	// arrange
	fake := newFakeFCM(t)
	client := fake.client(t)

	// act
	require.NoError(t, client.Wake(t.Context(), "a", now))
	require.NoError(t, client.Wake(t.Context(), "b", now.Add(30*time.Minute)))
	require.NoError(t, client.Wake(t.Context(), "c", now.Add(56*time.Minute)))

	// assert
	assert.Equal(t, 2, fake.tokenRequests(), "a token lives an hour and is renewed five minutes early")
	assert.Equal(t, "Bearer access-token-2", fake.lastAuthorization())
}

func TestClient_Wake_unregisteredInstallation(t *testing.T) {
	t.Parallel()

	// arrange
	fake := newFakeFCM(t)
	client := fake.client(t)

	// act
	err := client.Wake(t.Context(), "stale", now)

	// assert
	require.ErrorIs(t, err, fcm.ErrUnregistered)
}

func TestClient_Wake_otherFailure(t *testing.T) {
	t.Parallel()

	// arrange
	fake := newFakeFCM(t)
	client := fake.client(t)

	// act
	err := client.Wake(t.Context(), "flaky", now)

	// assert
	require.Error(t, err)
	require.NotErrorIs(t, err, fcm.ErrUnregistered)
	require.ErrorIs(t, err, fcm.ErrNotDelivered, "FCM answered, so the message surely did not go out")
	assert.ErrorContains(t, err, "503")
}

func TestClient_Wake_connectionLostLeavesTheOutcomeUnknown(t *testing.T) {
	t.Parallel()

	// arrange: the access token is cached, then the server goes away
	fake := newFakeFCM(t)
	client := fake.client(t)
	require.NoError(t, client.Wake(t.Context(), "installation-1", now))
	fake.srv.Close()

	// act
	err := client.Wake(t.Context(), "installation-1", now)

	// assert
	require.Error(t, err)
	assert.NotErrorIs(t, err, fcm.ErrNotDelivered)
}

func TestClient_Wake_notConfigured(t *testing.T) {
	t.Parallel()

	// arrange
	fake := newFakeFCM(t)
	client := fcm.NewClient(fcm.Config{BaseURL: fake.srv.URL, TokenURL: fake.srv.URL + "/token"}, fake.srv.Client())

	// act
	err := client.Wake(t.Context(), "installation-1", now)

	// assert
	require.NoError(t, err)
	assert.Equal(t, 0, fake.tokenRequests())
	assert.Nil(t, fake.lastMessage())
}

type sentMessage struct {
	Message struct {
		Fid          string            `json:"fid"`
		Data         map[string]string `json:"data"`
		Notification map[string]any    `json:"notification"`
		Android      struct {
			Priority string `json:"priority"`
		} `json:"android"`
	} `json:"message"`
}

type fakeFCM struct {
	srv *httptest.Server
	key *rsa.PrivateKey

	mu            sync.Mutex
	tokens        int
	assertion     string
	authorization string
	message       *sentMessage
}

func newFakeFCM(t *testing.T) *fakeFCM {
	t.Helper()

	key, err := rsa.GenerateKey(rand.Reader, 2048)
	require.NoError(t, err)
	f := &fakeFCM{key: key}
	mux := http.NewServeMux()
	mux.HandleFunc("POST /token", f.serveToken)
	mux.HandleFunc("POST /v1/projects/"+projectID+"/messages:send", f.serveSend)
	f.srv = httptest.NewServer(mux)
	t.Cleanup(f.srv.Close)
	return f
}

func (f *fakeFCM) client(t *testing.T) fcm.Client {
	t.Helper()

	account, err := fcm.ParseServiceAccount(serviceAccountJSON(t, f.key))
	require.NoError(t, err)
	return fcm.NewClient(fcm.Config{
		BaseURL:        f.srv.URL,
		TokenURL:       f.srv.URL + "/token",
		ServiceAccount: &account,
	}, f.srv.Client())
}

func (f *fakeFCM) serveToken(w http.ResponseWriter, r *http.Request) {
	_ = r.ParseForm()
	f.mu.Lock()
	defer f.mu.Unlock()
	if r.PostForm.Get("grant_type") != "urn:ietf:params:oauth:grant-type:jwt-bearer" {
		http.Error(w, `{"error":"unsupported_grant_type"}`, http.StatusBadRequest)
		return
	}
	f.tokens++
	f.assertion = r.PostForm.Get("assertion")
	_, _ = w.Write([]byte(`{"access_token":"access-token-` + strconv.Itoa(f.tokens) +
		`","expires_in":3600,"token_type":"Bearer"}`))
}

func (f *fakeFCM) serveSend(w http.ResponseWriter, r *http.Request) {
	body, _ := io.ReadAll(r.Body)
	var sent sentMessage
	_ = json.Unmarshal(body, &sent)
	f.mu.Lock()
	f.authorization = r.Header.Get("Authorization")
	f.message = &sent
	f.mu.Unlock()

	switch sent.Message.Fid {
	case "stale":
		w.WriteHeader(http.StatusNotFound)
		_, _ = w.Write([]byte(`{"error":{"code":404,"message":"Requested entity was not found.","status":"NOT_FOUND",` +
			`"details":[{"@type":"type.googleapis.com/google.firebase.fcm.v1.FcmError","errorCode":"UNREGISTERED"}]}}`))
	case "flaky":
		w.WriteHeader(http.StatusServiceUnavailable)
		_, _ = w.Write([]byte(`{"error":{"code":503,"message":"Try again","status":"UNAVAILABLE"}}`))
	default:
		_, _ = w.Write([]byte(`{"name":"projects/` + projectID + `/messages/1"}`))
	}
}

func (f *fakeFCM) tokenRequests() int {
	f.mu.Lock()
	defer f.mu.Unlock()
	return f.tokens
}

func (f *fakeFCM) lastAuthorization() string {
	f.mu.Lock()
	defer f.mu.Unlock()
	return f.authorization
}

func (f *fakeFCM) lastMessage() *sentMessage {
	f.mu.Lock()
	defer f.mu.Unlock()
	return f.message
}

func (f *fakeFCM) assertAssertion(t *testing.T) {
	t.Helper()

	f.mu.Lock()
	raw := f.assertion
	f.mu.Unlock()
	var claims struct {
		jwt.RegisteredClaims

		Scope string `json:"scope"`
	}
	_, err := jwt.ParseWithClaims(raw, &claims,
		func(*jwt.Token) (any, error) { return &f.key.PublicKey, nil },
		jwt.WithValidMethods([]string{"RS256"}),
		jwt.WithTimeFunc(func() time.Time { return now }),
	)
	require.NoError(t, err)
	assert.Equal(t, clientEmail, claims.Issuer)
	assert.Equal(t, jwt.ClaimStrings{f.srv.URL + "/token"}, claims.Audience)
	assert.Equal(t, "https://www.googleapis.com/auth/firebase.messaging", claims.Scope)
	assert.WithinDuration(t, now.Add(time.Hour), claims.ExpiresAt.Time, 0)
}

func serviceAccountJSON(t *testing.T, key *rsa.PrivateKey) string {
	t.Helper()

	der, err := x509.MarshalPKCS8PrivateKey(key)
	require.NoError(t, err)
	pemKey := pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: der})
	file, err := json.Marshal(map[string]string{
		"type":         "service_account",
		"project_id":   projectID,
		"client_email": clientEmail,
		"private_key":  string(pemKey),
	})
	require.NoError(t, err)
	return string(file)
}
