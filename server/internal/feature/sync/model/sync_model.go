package model

import (
	"uuid"

	cmodel "github.com/mickamy/LocateDo/internal/feature/category/model"
	hmodel "github.com/mickamy/LocateDo/internal/feature/household/model"
	pmodel "github.com/mickamy/LocateDo/internal/feature/place/model"
	tmodel "github.com/mickamy/LocateDo/internal/feature/todo/model"
)

// Kind names the table a tombstone came from.
type Kind string

const (
	KindMembership Kind = "memberships"
	KindCategory   Kind = "categories"
	KindPlace      Kind = "places"
	KindTodo       Kind = "todos"
)

// Deletion is a tombstone. For memberships, ID is the user id.
type Deletion struct {
	Kind    Kind
	ID      uuid.UUID
	Version int64
}

// Change is one element of a household's version-ordered stream; exactly one
// of the entity fields is set.
type Change struct {
	Version    int64
	Membership *hmodel.Membership
	Category   *cmodel.Category
	Place      *pmodel.Place
	Todo       *tmodel.Todo
	Deletion   *Deletion
}
