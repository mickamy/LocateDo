package seal

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"errors"
	"fmt"
)

const keyLen = 32

var (
	ErrKeyLength = fmt.Errorf("key must be %d bytes", keyLen)
	ErrOpen      = errors.New("cannot open sealed data")
)

// Box encrypts small secrets with AES-256-GCM. The additional data binds a
// ciphertext to its owner, so one row's secret cannot be copied into another.
type Box struct {
	aead cipher.AEAD
}

func NewBox(key []byte) (Box, error) {
	if len(key) != keyLen {
		return Box{}, ErrKeyLength
	}
	block, err := aes.NewCipher(key)
	if err != nil {
		return Box{}, fmt.Errorf("new cipher: %w", err)
	}
	aead, err := cipher.NewGCM(block)
	if err != nil {
		return Box{}, fmt.Errorf("new gcm: %w", err)
	}
	return Box{aead: aead}, nil
}

// Seal returns nonce || ciphertext.
func (b Box) Seal(plaintext, additionalData []byte) []byte {
	nonce := make([]byte, b.aead.NonceSize(), b.aead.NonceSize()+len(plaintext)+b.aead.Overhead())
	_, _ = rand.Read(nonce)
	return b.aead.Seal(nonce, nonce, plaintext, additionalData)
}

func (b Box) Open(sealed, additionalData []byte) ([]byte, error) {
	n := b.aead.NonceSize()
	if len(sealed) < n+b.aead.Overhead() {
		return nil, ErrOpen
	}
	plaintext, err := b.aead.Open(nil, sealed[:n], sealed[n:], additionalData)
	if err != nil {
		return nil, fmt.Errorf("%w: %w", ErrOpen, err)
	}
	return plaintext, nil
}
