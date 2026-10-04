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
	"github.com/mickamy/LocateDo/internal/infra/apple"
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

func signInInput(subject, displayName string) usecase.SignInWithAppleInput {
	return usecase.SignInWithAppleInput{
		IdentityToken:     "identity:" + subject,
		AuthorizationCode: "auth-code",
		Nonce:             "raw-nonce",
		DisplayName:       displayName,
	}
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
