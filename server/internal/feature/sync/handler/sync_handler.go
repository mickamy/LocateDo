package handler

import (
	"context"

	"connectrpc.com/connect"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/errors/cerrors"
	hmapper "github.com/mickamy/LocateDo/internal/feature/household/mapper"
	"github.com/mickamy/LocateDo/internal/feature/sync/mapper"
	"github.com/mickamy/LocateDo/internal/feature/sync/model"
	"github.com/mickamy/LocateDo/internal/feature/sync/usecase"
	syncv1 "github.com/mickamy/LocateDo/internal/gen/locatedo/sync/v1"
	"github.com/mickamy/LocateDo/internal/gen/locatedo/sync/v1/syncv1connect"
	"github.com/mickamy/LocateDo/internal/lib/caller"
	"github.com/mickamy/LocateDo/internal/lib/ids"
)

type Sync struct {
	_    di.Infra      `di:"embed"`
	pull *usecase.Pull `di:""`
}

var _ syncv1connect.SyncServiceHandler = (*Sync)(nil)

func (h *Sync) Pull(
	ctx context.Context,
	req *connect.Request[syncv1.PullRequest],
) (*connect.Response[syncv1.PullResponse], error) {
	householdID, err := caller.HouseholdID(ctx)
	if err != nil {
		return nil, cerrors.Map(err)
	}
	requested, err := ids.Parse("household_id", req.Msg.GetHouseholdId())
	if err != nil {
		return nil, cerrors.Map(err)
	}

	out, err := h.pull.Do(ctx, usecase.PullInput{
		HouseholdID:          householdID,
		RequestedHouseholdID: requested,
		Cursor:               req.Msg.GetCursor(),
		Limit:                req.Msg.GetLimit(),
	})
	if err != nil {
		return nil, cerrors.Map(err)
	}

	changes := make([]*syncv1.Change, len(out.Changes))
	for i, c := range out.Changes {
		changes[i] = changeToSyncv1(c)
	}
	return connect.NewResponse(&syncv1.PullResponse{
		Changes:   changes,
		Cursor:    out.Cursor,
		HasMore:   out.HasMore,
		Household: hmapper.HouseholdToHouseholdv1(out.Household),
	}), nil
}

func changeToSyncv1(c model.Change) *syncv1.Change {
	switch {
	case c.Membership != nil:
		return &syncv1.Change{Kind: &syncv1.Change_Membership{Membership: mapper.MembershipToHouseholdv1(*c.Membership)}}
	case c.Category != nil:
		return &syncv1.Change{Kind: &syncv1.Change_Category{Category: mapper.CategoryToCategoryv1(*c.Category)}}
	case c.Place != nil:
		return &syncv1.Change{Kind: &syncv1.Change_Place{Place: mapper.PlaceToPlacev1(*c.Place)}}
	case c.Todo != nil:
		return &syncv1.Change{Kind: &syncv1.Change_Todo{Todo: mapper.TodoToTodov1(*c.Todo)}}
	case c.Deletion != nil:
		return &syncv1.Change{Kind: &syncv1.Change_Deletion{Deletion: mapper.DeletionToSyncv1(*c.Deletion)}}
	default:
		return &syncv1.Change{}
	}
}
