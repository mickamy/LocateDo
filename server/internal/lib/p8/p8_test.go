package p8_test

import (
	"crypto/ecdsa"
	"crypto/ed25519"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/x509"
	"encoding/base64"
	"encoding/pem"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/lib/p8"
)

func TestParse(t *testing.T) {
	t.Parallel()

	// arrange
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	require.NoError(t, err)
	file := encode(t, key)

	tests := map[string]string{
		"pem":                   string(file),
		"base64":                base64.StdEncoding.EncodeToString(file),
		"base64 with a newline": base64.StdEncoding.EncodeToString(file) + "\n",
	}
	for name, raw := range tests {
		t.Run(name, func(t *testing.T) {
			t.Parallel()

			// act
			got, err := p8.Parse(raw)

			// assert
			require.NoError(t, err)
			assert.True(t, key.Equal(got))
		})
	}
}

func TestParse_rejects(t *testing.T) {
	t.Parallel()

	_, notECDSA, err := ed25519.GenerateKey(rand.Reader)
	require.NoError(t, err)

	tests := map[string]string{
		"garbage":        "not a key",
		"base64 of text": base64.StdEncoding.EncodeToString([]byte("not a pem")),
		"not ecdsa":      string(encode(t, notECDSA)),
	}
	for name, raw := range tests {
		t.Run(name, func(t *testing.T) {
			t.Parallel()

			_, err := p8.Parse(raw)

			require.Error(t, err)
		})
	}
}

func encode(t *testing.T, key any) []byte {
	t.Helper()

	der, err := x509.MarshalPKCS8PrivateKey(key)
	require.NoError(t, err)
	return pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: der})
}
