package usecase

import (
	"cmp"
	"context"
	"fmt"
	"slices"
	"uuid"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	hmodel "github.com/mickamy/LocateDo/internal/feature/household/model"
	hrepository "github.com/mickamy/LocateDo/internal/feature/household/repository"
	"github.com/mickamy/LocateDo/internal/feature/sync/model"
	"github.com/mickamy/LocateDo/internal/feature/sync/repository"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
)

// PullInput carries the caller's household and the one the device asks for;
// the two must agree. Cursor is the version the device last received, 0 for
// a first sync. Limit 0 means the default page size.
type PullInput struct {
	HouseholdID          uuid.UUID
	RequestedHouseholdID uuid.UUID
	Cursor               int64
	Limit                int32
}

// PullOutput is one page of the household's version-ordered stream. Cursor is
// what the device sends next: the last change's version while HasMore is
// set, otherwise the household version the page was read at.
type PullOutput struct {
	Changes   []model.Change
	Cursor    int64
	HasMore   bool
	Household hmodel.Household
}

// Pull reads every table and the tombstones after the cursor in one snapshot
// and merges them by version. A first sync skips tombstones: the device has
// never seen the rows they refer to.
type Pull struct {
	_              di.Infra              `di:"embed"`
	readTransactor tx.ReadTransactor     `di:""`
	households     hrepository.Household `di:""`
	changes        repository.Changes    `di:""`
}

func (uc Pull) Do(ctx context.Context, in PullInput) (PullOutput, error) {
	if in.RequestedHouseholdID != in.HouseholdID {
		return PullOutput{}, aerrors.PermissionDenied("not a member of this household")
	}
	limit := min(cmp.Or(in.Limit, model.DefaultPageSize), model.MaxPageSize)

	var out PullOutput
	if err := uc.readTransactor.WithReadTx(ctx, func(tx tx.Tx) error {
		h, err := uc.households.Bind(tx).Find(ctx, in.HouseholdID)
		if err != nil {
			return fmt.Errorf("find household: %w", err)
		}
		changes := uc.changes.Bind(tx)
		// One row past the page from every table tells whether more follow.
		fetch := limit + 1

		memberships, err := changes.Memberships(ctx, in.HouseholdID, in.Cursor, fetch)
		if err != nil {
			return fmt.Errorf("list memberships: %w", err)
		}
		categories, err := changes.Categories(ctx, in.HouseholdID, in.Cursor, fetch)
		if err != nil {
			return fmt.Errorf("list categories: %w", err)
		}
		places, err := changes.Places(ctx, in.HouseholdID, in.Cursor, fetch)
		if err != nil {
			return fmt.Errorf("list places: %w", err)
		}
		todos, err := changes.Todos(ctx, in.HouseholdID, in.Cursor, fetch)
		if err != nil {
			return fmt.Errorf("list todos: %w", err)
		}
		var deletions []model.Deletion
		if in.Cursor > 0 {
			if deletions, err = changes.Deletions(ctx, in.HouseholdID, in.Cursor, fetch); err != nil {
				return fmt.Errorf("list deletions: %w", err)
			}
		}

		all := make([]model.Change, 0, len(memberships)+len(categories)+len(places)+len(todos)+len(deletions))
		for _, m := range memberships {
			all = append(all, model.Change{Version: m.Version, Membership: &m})
		}
		for _, c := range categories {
			all = append(all, model.Change{Version: c.Version, Category: &c})
		}
		for _, p := range places {
			all = append(all, model.Change{Version: p.Version, Place: &p})
		}
		for _, t := range todos {
			all = append(all, model.Change{Version: t.Version, Todo: &t})
		}
		for _, d := range deletions {
			all = append(all, model.Change{Version: d.Version, Deletion: &d})
		}
		slices.SortFunc(all, func(a, b model.Change) int { return cmp.Compare(a.Version, b.Version) })

		hasMore := len(all) > int(limit)
		if hasMore {
			all = all[:limit]
		}
		cursor := h.Version
		if hasMore {
			cursor = all[len(all)-1].Version
		}
		out = PullOutput{Changes: all, Cursor: cursor, HasMore: hasMore, Household: h}
		return nil
	}); err != nil {
		return PullOutput{}, fmt.Errorf("pull: %w", err)
	}
	return out, nil
}
