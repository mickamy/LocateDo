package usecase_test

import (
	"context"
	"errors"
	"strings"
	"sync"
	"testing"
	"time"
	"uuid"

	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/feature/account/repository"
	"github.com/mickamy/LocateDo/internal/feature/account/usecase"
	dfixture "github.com/mickamy/LocateDo/internal/feature/device/fixture"
	dmodel "github.com/mickamy/LocateDo/internal/feature/device/model"
	drepository "github.com/mickamy/LocateDo/internal/feature/device/repository"
	"github.com/mickamy/LocateDo/internal/infra/apple"
	"github.com/mickamy/LocateDo/internal/infra/google"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/test/tdb"
)

var now = time.Date(2026, 10, 3, 12, 0, 0, 0, time.UTC)

func fixedClock(t *testing.T) context.Context {
	t.Helper()

	return clock.Set(t.Context(), clock.NewFixed(now))
}

// fakedApple returns the wiring with the Apple client replaced by a fake.
func fakedApple(d tdb.DB) (di.Infra, *fakeApple) {
	fake := &fakeApple{}
	infra := d.Infra()
	infra.Apple = fake
	return infra, fake
}

// fakedGoogle returns the wiring with the Google client replaced by a fake.
func fakedGoogle(d tdb.DB) di.Infra {
	infra := d.Infra()
	infra.Google = fakeGoogle{}
	return infra
}

func newLib() di.Lib {
	return di.MustNewLib(di.NewConfig())
}

func openAppleToken(t *testing.T, d tdb.DB, lib di.Lib, userID uuid.UUID) string {
	t.Helper()

	sealed, err := repository.NewAppleToken(d.Reader).Find(t.Context(), userID)
	require.NoError(t, err)
	plain, err := lib.Box.Open(sealed, userID[:])
	require.NoError(t, err)
	return string(plain)
}

func registerDevice(t *testing.T, d tdb.DB, userID uuid.UUID) dmodel.Device {
	t.Helper()

	device := dfixture.Device(func(m *dmodel.Device) { m.UserID = new(userID); m.LastSeenAt = now })
	d.InTx(t, func(tx tx.Tx) {
		_, err := drepository.NewDevice(d.Reader).Bind(tx).Upsert(t.Context(), device)
		require.NoError(t, err)
	})
	return device
}

func countDevices(t *testing.T, d tdb.DB, pushToken string) int {
	t.Helper()

	var n int
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"SELECT count(*) FROM devices WHERE push_token = $1", pushToken).Scan(&n))
	return n
}

func signInInput(subject, displayName string) usecase.SignInWithAppleInput {
	return usecase.SignInWithAppleInput{
		IdentityToken:     "identity:" + subject,
		AuthorizationCode: "auth-code",
		Nonce:             "raw-nonce",
		DisplayName:       displayName,
	}
}

func googleInput(subject string) usecase.SignInWithGoogleInput {
	return usecase.SignInWithGoogleInput{IDToken: "google:" + subject, Nonce: "0123456789abcdef"}
}

// fakeGoogle accepts id tokens of the form "google:<subject>" and names every user "Google User".
type fakeGoogle struct{}

var _ google.Auth = fakeGoogle{}

func (fakeGoogle) VerifyIDToken(_ context.Context, raw, nonce string, _ time.Time) (google.Identity, error) {
	subject, ok := strings.CutPrefix(raw, "google:")
	if !ok || nonce == "" {
		return google.Identity{}, google.ErrInvalidToken
	}
	return google.Identity{Subject: subject, Name: "Google User"}, nil
}

// fakeApple accepts identity tokens of the form "identity:<subject>" and
// exchanges any code except "expired-code" for "apple-refresh:<code>".
type fakeApple struct {
	mu         sync.Mutex
	failRevoke bool
	revoked    []string
}

var _ apple.Auth = (*fakeApple)(nil)

func (f *fakeApple) VerifyIdentityToken(_ context.Context, raw, rawNonce string, _ time.Time) (apple.Identity, error) {
	subject, ok := strings.CutPrefix(raw, "identity:")
	if !ok || rawNonce == "" {
		return apple.Identity{}, apple.ErrInvalidToken
	}
	return apple.Identity{Subject: subject}, nil
}

func (f *fakeApple) ExchangeCode(_ context.Context, code string, _ time.Time) (string, error) {
	if code == "expired-code" {
		return "", errors.New("status 400: invalid_grant")
	}
	return "apple-refresh:" + code, nil
}

func (f *fakeApple) Revoke(_ context.Context, refreshToken string, _ time.Time) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.failRevoke {
		return errors.New("status 503")
	}
	f.revoked = append(f.revoked, refreshToken)
	return nil
}

func (f *fakeApple) revokedTokens() []string {
	f.mu.Lock()
	defer f.mu.Unlock()
	return f.revoked
}
