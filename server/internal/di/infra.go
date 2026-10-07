package di

import (
	"context"
	"fmt"
	"net/http"
	"time"

	"github.com/mickamy/LocateDo/config"
	"github.com/mickamy/LocateDo/internal/infra/apns"
	"github.com/mickamy/LocateDo/internal/infra/apple"
	"github.com/mickamy/LocateDo/internal/infra/fcm"
	"github.com/mickamy/LocateDo/internal/infra/google"
	"github.com/mickamy/LocateDo/internal/infra/revenuecat"
	"github.com/mickamy/LocateDo/internal/infra/storage/db"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/internal/lib/p8"
)

const (
	appleHTTPTimeout  = 10 * time.Second
	googleHTTPTimeout = 10 * time.Second
	apnsHTTPTimeout   = 10 * time.Second
	fcmHTTPTimeout    = 10 * time.Second
	revenueCatTimeout = 10 * time.Second
)

//kanna:container must returns=Infra
type Infra struct {
	_              context.Context         `di:"arg"` //nolint:containedctx // required by kanna-di
	_              Config                  `di:"embed"`
	Writer         db.Writer               `di:"with=provideWriter"`
	Reader         db.Reader               `di:"with=provideReader"`
	Transactor     tx.Transactor           `di:"with=provideTransactor"`
	ReadTransactor tx.ReadTransactor       `di:"with=provideReadTransactor"`
	Apple          apple.Auth              `di:"with=provideApple"`
	Google         google.Auth             `di:"with=provideGoogle"`
	APNs           apns.Pusher             `di:"with=provideAPNs"`
	FCM            fcm.Pusher              `di:"with=provideFCM"`
	Entitlements   revenuecat.Entitlements `di:"with=provideRevenueCat"`
}

func (infra *Infra) Close() error {
	infra.Writer.Close()
	infra.Reader.Close()
	return nil
}

func provideWriter(ctx context.Context, cfg config.Database) (db.Writer, error) {
	writer, err := db.NewWriter(ctx, cfg.WriterURL)
	if err != nil {
		return db.Writer{}, fmt.Errorf("new writer: %w", err)
	}
	return writer, nil
}

func provideReader(ctx context.Context, cfg config.Database, writer db.Writer) (db.Reader, error) {
	reader, err := db.NewReader(ctx, cfg.ReaderURL)
	if err != nil {
		writer.Close()
		return db.Reader{}, fmt.Errorf("new reader: %w", err)
	}
	return reader, nil
}

func provideTransactor(writer db.Writer) tx.Transactor {
	return tx.NewTransactor(writer)
}

func provideReadTransactor(reader db.Reader) tx.ReadTransactor {
	return tx.NewReadTransactor(reader)
}

func provideApple(cfg config.Apple) (apple.Auth, error) {
	appleCfg := apple.Config{
		BaseURL:    cfg.BaseURL,
		BundleID:   cfg.BundleID,
		ServicesID: cfg.ServicesID,
		TeamID:     cfg.TeamID,
		KeyID:      cfg.KeyID,
	}
	if cfg.PrivateKey != "" {
		key, err := p8.Parse(cfg.PrivateKey)
		if err != nil {
			return nil, fmt.Errorf("parse APPLE_PRIVATE_KEY: %w", err)
		}
		appleCfg.PrivateKey = key
	}
	return apple.NewClient(appleCfg, &http.Client{Timeout: appleHTTPTimeout}), nil
}

func provideGoogle(cfg config.Google) google.Auth {
	return google.NewClient(
		google.Config{BaseURL: cfg.BaseURL, ClientID: cfg.ClientID},
		&http.Client{Timeout: googleHTTPTimeout},
	)
}

func provideAPNs(cfg config.APNs, appleCfg config.Apple) (apns.Pusher, error) {
	apnsCfg := apns.Config{
		ProductionURL: apns.ProductionURL,
		SandboxURL:    apns.SandboxURL,
		Topic:         cfg.Topic,
		TeamID:        appleCfg.TeamID,
		KeyID:         cfg.KeyID,
	}
	if cfg.PrivateKey != "" {
		key, err := p8.Parse(cfg.PrivateKey)
		if err != nil {
			return nil, fmt.Errorf("parse APNS_PRIVATE_KEY: %w", err)
		}
		apnsCfg.PrivateKey = key
	}
	return apns.NewClient(apnsCfg, &http.Client{Timeout: apnsHTTPTimeout}), nil
}

func provideFCM(cfg config.FCM) (fcm.Pusher, error) {
	fcmCfg := fcm.Config{BaseURL: fcm.DefaultBaseURL, TokenURL: fcm.DefaultTokenURL}
	if cfg.ServiceAccount != "" {
		account, err := fcm.ParseServiceAccount(cfg.ServiceAccount)
		if err != nil {
			return nil, fmt.Errorf("parse FCM_SERVICE_ACCOUNT: %w", err)
		}
		fcmCfg.ServiceAccount = &account
	}
	return fcm.NewClient(fcmCfg, &http.Client{Timeout: fcmHTTPTimeout}), nil
}

func provideRevenueCat(cfg config.RevenueCat) revenuecat.Entitlements {
	return revenuecat.NewClient(revenuecat.Config{
		BaseURL:       cfg.BaseURL,
		APIKey:        cfg.APIKey,
		ProjectID:     cfg.ProjectID,
		EntitlementID: cfg.EntitlementID,
	}, &http.Client{Timeout: revenueCatTimeout})
}
