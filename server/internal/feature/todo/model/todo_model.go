package model

import (
	"time"
	"uuid"
)

type Todo struct {
	ID          uuid.UUID
	HouseholdID uuid.UUID
	PlaceID     uuid.UUID
	Title       string
	AssigneeID  *uuid.UUID
	CompletedAt *time.Time
	UpdatedAt   time.Time
	Version     int64
}
