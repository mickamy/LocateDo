package repository

import (
	"context"
	"fmt"
	"time"
	"uuid"

	"github.com/mickamy/LocateDo/internal/feature/sync/queries"
	"github.com/mickamy/LocateDo/internal/infra/storage/db"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
)

type Tombstones interface {
	// Sweep deletes tombstones older than before and records, per household,
	// the newest version it removed. It reports how many went.
	Sweep(ctx context.Context, before time.Time) (int, error)
	Bind(tx tx.Tx) Tombstones
}

type tombstones struct {
	q *queries.Queries
}

var _ Tombstones = tombstones{}

func NewTombstones(reader db.Reader) Tombstones {
	return tombstones{q: queries.New(reader)}
}

func (r tombstones) Bind(tx tx.Tx) Tombstones {
	return tombstones{q: queries.New(tx.DBTX())}
}

func (r tombstones) Sweep(ctx context.Context, before time.Time) (int, error) {
	gone, err := r.q.SweepDeletions(ctx, before)
	if err != nil {
		return 0, fmt.Errorf("sweep deletions: %w", err)
	}
	newest := make(map[uuid.UUID]int64, len(gone))
	for _, g := range gone {
		newest[g.HouseholdID] = max(newest[g.HouseholdID], g.Version)
	}
	for householdID, version := range newest {
		if err := r.q.RaiseSweptVersion(ctx, queries.RaiseSweptVersionParams{ID: householdID, Version: version}); err != nil {
			return 0, fmt.Errorf("raise swept version: %w", err)
		}
	}
	return len(gone), nil
}
