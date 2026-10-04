package model

import (
	"time"
	"uuid"
)

type Category struct {
	ID          uuid.UUID
	HouseholdID uuid.UUID
	BuiltinKey  *string `map:"Builtin"`
	Name        *string
	Icon        string
	Color       string
	SortOrder   int32
	UpdatedAt   time.Time
	Version     int64
}
