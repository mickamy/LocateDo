package token

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"errors"
	"fmt"
	"time"
	"uuid"

	"github.com/golang-jwt/jwt/v5"
)

const (
	AccessTTL  = time.Hour
	RefreshTTL = 90 * 24 * time.Hour

	issuer         = "locatedo"
	minKeyLength   = 32
	refreshByteLen = 32
)

var (
	ErrInvalid  = errors.New("invalid token")
	ErrShortKey = fmt.Errorf("signing key must be at least %d bytes", minKeyLength)
)

type Signer struct {
	key []byte
}

func NewSigner(key []byte) (Signer, error) {
	if len(key) < minKeyLength {
		return Signer{}, ErrShortKey
	}
	return Signer{key: key}, nil
}

func (s Signer) IssueAccess(userID uuid.UUID, now time.Time) (string, time.Time, error) {
	expiresAt := now.Add(AccessTTL)
	claims := jwt.RegisteredClaims{
		Issuer:    issuer,
		Subject:   userID.String(),
		IssuedAt:  jwt.NewNumericDate(now),
		ExpiresAt: jwt.NewNumericDate(expiresAt),
	}
	signed, err := jwt.NewWithClaims(jwt.SigningMethodHS256, claims).SignedString(s.key)
	if err != nil {
		return "", time.Time{}, fmt.Errorf("sign access token: %w", err)
	}
	return signed, expiresAt, nil
}

func (s Signer) VerifyAccess(raw string, now time.Time) (uuid.UUID, error) {
	var claims jwt.RegisteredClaims
	_, err := jwt.ParseWithClaims(raw, &claims,
		func(*jwt.Token) (any, error) { return s.key, nil },
		jwt.WithValidMethods([]string{jwt.SigningMethodHS256.Alg()}),
		jwt.WithIssuer(issuer),
		jwt.WithExpirationRequired(),
		jwt.WithTimeFunc(func() time.Time { return now }),
	)
	if err != nil {
		return uuid.Nil(), fmt.Errorf("%w: %w", ErrInvalid, err)
	}

	userID, err := uuid.Parse(claims.Subject)
	if err != nil {
		return uuid.Nil(), fmt.Errorf("%w: subject: %w", ErrInvalid, err)
	}
	return userID, nil
}

// NewRefresh returns an opaque token for the client and the hash to store.
func NewRefresh() (string, []byte) {
	b := make([]byte, refreshByteLen)
	_, _ = rand.Read(b)
	raw := base64.RawURLEncoding.EncodeToString(b)
	return raw, HashRefresh(raw)
}

func HashRefresh(raw string) []byte {
	sum := sha256.Sum256([]byte(raw))
	return sum[:]
}
