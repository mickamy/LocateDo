// Package p8 reads Apple's .p8 private keys.
package p8

import (
	"crypto/ecdsa"
	"crypto/x509"
	"encoding/base64"
	"encoding/pem"
	"errors"
	"fmt"
	"strings"
)

// Parse reads a key given as the PEM file or as base64 of it, the one-line
// form that fits in a .env file.
func Parse(raw string) (*ecdsa.PrivateKey, error) {
	raw = strings.TrimSpace(raw)
	pemBytes := []byte(raw)
	if !strings.HasPrefix(raw, "-----BEGIN") {
		decoded, err := base64.StdEncoding.DecodeString(raw)
		if err != nil {
			return nil, fmt.Errorf("decode base64: %w", err)
		}
		pemBytes = decoded
	}

	block, _ := pem.Decode(pemBytes)
	if block == nil {
		return nil, errors.New("decode p8: no PEM block")
	}
	key, err := x509.ParsePKCS8PrivateKey(block.Bytes)
	if err != nil {
		return nil, fmt.Errorf("parse p8: %w", err)
	}
	ecKey, ok := key.(*ecdsa.PrivateKey)
	if !ok {
		return nil, fmt.Errorf("parse p8: want ECDSA key, got %T", key)
	}
	return ecKey, nil
}
