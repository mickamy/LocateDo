package handler

import "github.com/mickamy/LocateDo/internal/gen/locatedo/sync/v1/syncv1connect"

type Sync struct {
	syncv1connect.UnimplementedSyncServiceHandler
}

var _ syncv1connect.SyncServiceHandler = (*Sync)(nil)

func NewSync() *Sync {
	return &Sync{}
}
