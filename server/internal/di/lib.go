package di

import (
	"encoding/base64"
	"fmt"

	"github.com/mickamy/LocateDo/config"
	"github.com/mickamy/LocateDo/internal/lib/seal"
	"github.com/mickamy/LocateDo/internal/lib/token"
)

//kanna:container must returns=Lib
type Lib struct {
	_      Config       `di:"embed"`
	Signer token.Signer `di:"with=provideSigner"`
	Box    seal.Box     `di:"with=provideBox"`
}

func provideSigner(cfg config.Auth) (token.Signer, error) {
	signer, err := token.NewSigner([]byte(cfg.JWTSigningKey))
	if err != nil {
		return token.Signer{}, fmt.Errorf("new signer: %w", err)
	}
	return signer, nil
}

func provideBox(cfg config.Auth) (seal.Box, error) {
	key, err := base64.StdEncoding.DecodeString(cfg.SealKey)
	if err != nil {
		return seal.Box{}, fmt.Errorf("decode SEAL_KEY: %w", err)
	}
	box, err := seal.NewBox(key)
	if err != nil {
		return seal.Box{}, fmt.Errorf("new box: %w", err)
	}
	return box, nil
}
