package interceptor

import (
	"context"
	"errors"
	"strings"

	"connectrpc.com/connect"

	"github.com/mickamy/LocateDo/internal/gen/locatedo/account/v1/accountv1connect"
	"github.com/mickamy/LocateDo/internal/gen/locatedo/device/v1/devicev1connect"
	"github.com/mickamy/LocateDo/internal/lib/caller"
	"github.com/mickamy/LocateDo/internal/lib/clock"
	"github.com/mickamy/LocateDo/internal/lib/token"
)

var publicProcedures = map[string]bool{
	accountv1connect.AccountServiceSignInWithAppleProcedure:         true,
	accountv1connect.AccountServiceSignInWithGoogleProcedure:        true,
	accountv1connect.AccountServiceRefreshTokenProcedure:            true,
	accountv1connect.AccountServiceSignOutProcedure:                 true,
	accountv1connect.AccountServiceDeleteAccountWithAppleProcedure:  true,
	accountv1connect.AccountServiceDeleteAccountWithGoogleProcedure: true,
}

var optionalAuthProcedures = map[string]bool{
	devicev1connect.DeviceServiceRegisterDeviceProcedure: true,
}

var (
	errMissingToken = errors.New("missing bearer token")
	errInvalidToken = errors.New("invalid access token")
)

// Auth requires a valid access token on every procedure except sign-in,
// refresh, sign-out, and deletion with a provider token, and puts the caller's user ID in the context. A
// procedure with optional auth runs anonymously only when no token is sent.
func Auth(signer token.Signer) connect.UnaryInterceptorFunc {
	return func(next connect.UnaryFunc) connect.UnaryFunc {
		return func(ctx context.Context, req connect.AnyRequest) (connect.AnyResponse, error) {
			if publicProcedures[req.Spec().Procedure] {
				return next(ctx, req)
			}

			header := req.Header().Get("Authorization")
			if header == "" && optionalAuthProcedures[req.Spec().Procedure] {
				return next(ctx, req)
			}
			raw, ok := strings.CutPrefix(header, "Bearer ")
			if !ok || raw == "" {
				return nil, connect.NewError(connect.CodeUnauthenticated, errMissingToken)
			}
			userID, err := signer.VerifyAccess(raw, clock.Now(ctx))
			if err != nil {
				return nil, connect.NewError(connect.CodeUnauthenticated, errInvalidToken)
			}

			return next(caller.Set(ctx, userID), req)
		}
	}
}
