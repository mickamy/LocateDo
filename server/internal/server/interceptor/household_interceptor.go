package interceptor

import (
	"context"
	"errors"
	"fmt"

	"connectrpc.com/connect"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/errors/cerrors"
	hrepository "github.com/mickamy/LocateDo/internal/feature/household/repository"
	"github.com/mickamy/LocateDo/internal/lib/caller"
)

// Household looks the caller's household up once per request and puts it in
// the context. A caller without one, or a public procedure, passes through.
func Household(memberships hrepository.Membership) connect.UnaryInterceptorFunc {
	return func(next connect.UnaryFunc) connect.UnaryFunc {
		return func(ctx context.Context, req connect.AnyRequest) (connect.AnyResponse, error) {
			userID, err := caller.UserID(ctx)
			if err != nil {
				return next(ctx, req)
			}

			m, err := memberships.FindByUser(ctx, userID)
			if errors.Is(err, aerrors.ErrNotFound) {
				return next(ctx, req)
			}
			if err != nil {
				return nil, cerrors.Map(fmt.Errorf("resolve household: %w", err))
			}
			return next(caller.SetHousehold(ctx, m.HouseholdID), req)
		}
	}
}
