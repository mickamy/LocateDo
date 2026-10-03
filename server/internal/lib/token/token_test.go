package token_test

import (
	"crypto/rand"
	"crypto/rsa"
	"strings"
	"testing"
	"time"
	"uuid"

	"github.com/golang-jwt/jwt/v5"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/lib/token"
)

var (
	testKey  = []byte(strings.Repeat("k", 32))
	otherKey = []byte(strings.Repeat("o", 32))
	now      = time.Date(2026, 10, 3, 12, 0, 0, 0, time.UTC)
)

func TestNewSigner_shortKey(t *testing.T) {
	t.Parallel()

	_, err := token.NewSigner([]byte("short"))

	require.ErrorIs(t, err, token.ErrShortKey)
}

func TestSigner_roundTrip(t *testing.T) {
	t.Parallel()

	// arrange
	signer := mustSigner(t, testKey)
	userID := uuid.NewV7()

	// act
	raw, expiresAt, err := signer.IssueAccess(userID, now)
	require.NoError(t, err)
	got, err := signer.VerifyAccess(raw, now.Add(59*time.Minute))

	// assert
	require.NoError(t, err)
	assert.Equal(t, userID, got)
	assert.Equal(t, now.Add(token.AccessTTL), expiresAt)
}

func TestSigner_VerifyAccess_rejects(t *testing.T) {
	t.Parallel()

	userID := uuid.NewV7()
	valid := issue(t, testKey, userID)

	tests := []struct {
		name string
		raw  string
		at   time.Time
	}{
		{name: "expired", raw: valid, at: now.Add(token.AccessTTL + time.Second)},
		{name: "other key", raw: issue(t, otherKey, userID), at: now},
		{name: "malformed", raw: "not.a.jwt", at: now},
		{name: "empty", raw: "", at: now},
		{name: "alg none", raw: signNone(t, userID), at: now},
		{name: "rs256", raw: signRS256(t, userID), at: now},
		{name: "wrong issuer", raw: signHS256(t, jwt.RegisteredClaims{
			Issuer:    "someone-else",
			Subject:   userID.String(),
			ExpiresAt: jwt.NewNumericDate(now.Add(time.Hour)),
		}), at: now},
		{name: "no expiry", raw: signHS256(t, jwt.RegisteredClaims{
			Issuer:  "locatedo",
			Subject: userID.String(),
		}), at: now},
		{name: "subject not a uuid", raw: signHS256(t, jwt.RegisteredClaims{
			Issuer:    "locatedo",
			Subject:   "alice",
			ExpiresAt: jwt.NewNumericDate(now.Add(time.Hour)),
		}), at: now},
	}

	signer := mustSigner(t, testKey)
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			_, err := signer.VerifyAccess(tt.raw, tt.at)

			require.ErrorIs(t, err, token.ErrInvalid)
		})
	}
}

func TestNewOpaque(t *testing.T) {
	t.Parallel()

	// act
	raw1, hash1 := token.NewOpaque()
	raw2, hash2 := token.NewOpaque()

	// assert
	assert.Len(t, raw1, 43)
	assert.NotEqual(t, raw1, raw2)
	assert.NotEqual(t, hash1, hash2)
	assert.Equal(t, hash1, token.HashOpaque(raw1))
	assert.NotContains(t, string(hash1), raw1)
}

func mustSigner(t *testing.T, key []byte) token.Signer {
	t.Helper()

	s, err := token.NewSigner(key)
	require.NoError(t, err)
	return s
}

func issue(t *testing.T, key []byte, userID uuid.UUID) string {
	t.Helper()

	raw, _, err := mustSigner(t, key).IssueAccess(userID, now)
	require.NoError(t, err)
	return raw
}

func signHS256(t *testing.T, claims jwt.RegisteredClaims) string {
	t.Helper()

	raw, err := jwt.NewWithClaims(jwt.SigningMethodHS256, claims).SignedString(testKey)
	require.NoError(t, err)
	return raw
}

func signNone(t *testing.T, userID uuid.UUID) string {
	t.Helper()

	claims := jwt.RegisteredClaims{
		Issuer:    "locatedo",
		Subject:   userID.String(),
		ExpiresAt: jwt.NewNumericDate(now.Add(time.Hour)),
	}
	raw, err := jwt.NewWithClaims(jwt.SigningMethodNone, claims).SignedString(jwt.UnsafeAllowNoneSignatureType)
	require.NoError(t, err)
	return raw
}

func signRS256(t *testing.T, userID uuid.UUID) string {
	t.Helper()

	key, err := rsa.GenerateKey(rand.Reader, 2048)
	require.NoError(t, err)
	claims := jwt.RegisteredClaims{
		Issuer:    "locatedo",
		Subject:   userID.String(),
		ExpiresAt: jwt.NewNumericDate(now.Add(time.Hour)),
	}
	raw, err := jwt.NewWithClaims(jwt.SigningMethodRS256, claims).SignedString(key)
	require.NoError(t, err)
	return raw
}
