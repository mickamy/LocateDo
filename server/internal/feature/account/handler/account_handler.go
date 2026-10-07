package handler

import (
	"context"
	"uuid"

	"connectrpc.com/connect"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/errors/cerrors"
	"github.com/mickamy/LocateDo/internal/feature/account/mapper"
	"github.com/mickamy/LocateDo/internal/feature/account/usecase"
	dmapper "github.com/mickamy/LocateDo/internal/feature/device/mapper"
	accountv1 "github.com/mickamy/LocateDo/internal/gen/locatedo/account/v1"
	"github.com/mickamy/LocateDo/internal/gen/locatedo/account/v1/accountv1connect"
	"github.com/mickamy/LocateDo/internal/infra/apple"
	"github.com/mickamy/LocateDo/internal/lib/caller"
	"github.com/mickamy/LocateDo/internal/lib/ptr"
)

type Account struct {
	accountv1connect.UnimplementedAccountServiceHandler

	_                di.Infra                         `di:"embed"`
	_                di.Lib                           `di:"embed"`
	signInWithApple  *usecase.SignInWithApple         `di:""`
	signInWithGoogle *usecase.SignInWithGoogle        `di:""`
	refreshToken     *usecase.RefreshToken            `di:""`
	signOut          *usecase.SignOut                 `di:""`
	deleteAccount    *usecase.DeleteAccount           `di:""`
	deleteWithApple  *usecase.DeleteAccountWithApple  `di:""`
	deleteWithGoogle *usecase.DeleteAccountWithGoogle `di:""`
	syncEntitlement  *usecase.SyncEntitlement         `di:""`
}

var _ accountv1connect.AccountServiceHandler = (*Account)(nil)

func (h *Account) SignInWithApple(
	ctx context.Context,
	req *connect.Request[accountv1.SignInWithAppleRequest],
) (*connect.Response[accountv1.SignInWithAppleResponse], error) {
	out, err := h.signInWithApple.Do(ctx, usecase.SignInWithAppleInput{
		IdentityToken:     req.Msg.GetIdentityToken(),
		AuthorizationCode: req.Msg.GetAuthorizationCode(),
		Nonce:             req.Msg.GetNonce(),
		DisplayName:       req.Msg.GetDisplayName(),
		Client:            appleClient(req.Msg.GetClient()),
	})
	if err != nil {
		return nil, cerrors.Map(err)
	}
	return connect.NewResponse(&accountv1.SignInWithAppleResponse{
		Session:     mapper.SessionToAccountv1(out.Session),
		HouseholdId: ptr.Map(out.HouseholdID, uuid.UUID.String),
	}), nil
}

func (h *Account) SignInWithGoogle(
	ctx context.Context,
	req *connect.Request[accountv1.SignInWithGoogleRequest],
) (*connect.Response[accountv1.SignInWithGoogleResponse], error) {
	out, err := h.signInWithGoogle.Do(ctx, usecase.SignInWithGoogleInput{
		IDToken: req.Msg.GetIdToken(),
		Nonce:   req.Msg.GetNonce(),
	})
	if err != nil {
		return nil, cerrors.Map(err)
	}
	return connect.NewResponse(&accountv1.SignInWithGoogleResponse{
		Session:     mapper.SessionToAccountv1(out.Session),
		HouseholdId: ptr.Map(out.HouseholdID, uuid.UUID.String),
	}), nil
}

func (h *Account) RefreshToken(
	ctx context.Context,
	req *connect.Request[accountv1.RefreshTokenRequest],
) (*connect.Response[accountv1.RefreshTokenResponse], error) {
	out, err := h.refreshToken.Do(ctx, usecase.RefreshTokenInput{RefreshToken: req.Msg.GetRefreshToken()})
	if err != nil {
		return nil, cerrors.Map(err)
	}
	return connect.NewResponse(&accountv1.RefreshTokenResponse{
		Session:     mapper.SessionToAccountv1(out.Session),
		HouseholdId: ptr.Map(out.HouseholdID, uuid.UUID.String),
	}), nil
}

func (h *Account) SignOut(
	ctx context.Context,
	req *connect.Request[accountv1.SignOutRequest],
) (*connect.Response[accountv1.SignOutResponse], error) {
	in := usecase.SignOutInput{RefreshToken: req.Msg.GetRefreshToken()}
	if device := req.Msg.GetDevice(); device != nil {
		in.Device = &usecase.SignOutDevice{
			Platform:  dmapper.PlatformFromDevicev1(device.GetPlatform()),
			PushToken: device.GetPushToken(),
		}
	}
	if err := h.signOut.Do(ctx, in); err != nil {
		return nil, cerrors.Map(err)
	}
	return connect.NewResponse(&accountv1.SignOutResponse{}), nil
}

func (h *Account) DeleteAccount(
	ctx context.Context,
	_ *connect.Request[accountv1.DeleteAccountRequest],
) (*connect.Response[accountv1.DeleteAccountResponse], error) {
	userID, err := caller.UserID(ctx)
	if err != nil {
		return nil, cerrors.Map(err)
	}
	if err := h.deleteAccount.Do(ctx, usecase.DeleteAccountInput{UserID: userID}); err != nil {
		return nil, cerrors.Map(err)
	}
	return connect.NewResponse(&accountv1.DeleteAccountResponse{}), nil
}

func (h *Account) DeleteAccountWithApple(
	ctx context.Context,
	req *connect.Request[accountv1.DeleteAccountWithAppleRequest],
) (*connect.Response[accountv1.DeleteAccountWithAppleResponse], error) {
	out, err := h.deleteWithApple.Do(ctx, usecase.DeleteAccountWithAppleInput{
		IdentityToken: req.Msg.GetIdentityToken(),
		Nonce:         req.Msg.GetNonce(),
	})
	if err != nil {
		return nil, cerrors.Map(err)
	}
	return connect.NewResponse(&accountv1.DeleteAccountWithAppleResponse{Deleted: out.Deleted}), nil
}

func (h *Account) DeleteAccountWithGoogle(
	ctx context.Context,
	req *connect.Request[accountv1.DeleteAccountWithGoogleRequest],
) (*connect.Response[accountv1.DeleteAccountWithGoogleResponse], error) {
	out, err := h.deleteWithGoogle.Do(ctx, usecase.DeleteAccountWithGoogleInput{
		IDToken: req.Msg.GetIdToken(),
		Nonce:   req.Msg.GetNonce(),
	})
	if err != nil {
		return nil, cerrors.Map(err)
	}
	return connect.NewResponse(&accountv1.DeleteAccountWithGoogleResponse{Deleted: out.Deleted}), nil
}

func (h *Account) SyncEntitlement(
	ctx context.Context,
	_ *connect.Request[accountv1.SyncEntitlementRequest],
) (*connect.Response[accountv1.SyncEntitlementResponse], error) {
	userID, err := caller.UserID(ctx)
	if err != nil {
		return nil, cerrors.Map(err)
	}
	if err := h.syncEntitlement.Do(ctx, usecase.SyncEntitlementInput{UserID: userID}); err != nil {
		return nil, cerrors.Map(err)
	}
	return connect.NewResponse(&accountv1.SyncEntitlementResponse{}), nil
}

func appleClient(c accountv1.AppleClient) apple.ClientKind {
	if c == accountv1.AppleClient_APPLE_CLIENT_SERVICES {
		return apple.ClientServices
	}
	return apple.ClientApp
}
