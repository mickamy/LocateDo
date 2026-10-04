package mapper

import (
	"github.com/go-kanna/kanna/mapper"

	"github.com/mickamy/LocateDo/internal/feature/sync/model"
	syncv1 "github.com/mickamy/LocateDo/internal/gen/locatedo/sync/v1"
)

func init() {
	mapper.Register(KindToEntityKind)
}

var entityKinds = map[model.Kind]syncv1.EntityKind{
	model.KindMembership: syncv1.EntityKind_ENTITY_KIND_MEMBERSHIP,
	model.KindCategory:   syncv1.EntityKind_ENTITY_KIND_CATEGORY,
	model.KindPlace:      syncv1.EntityKind_ENTITY_KIND_PLACE,
	model.KindTodo:       syncv1.EntityKind_ENTITY_KIND_TODO,
}

func KindToEntityKind(k model.Kind) syncv1.EntityKind {
	return entityKinds[k]
}
