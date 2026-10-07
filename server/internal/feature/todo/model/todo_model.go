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
	// CreatorID and CompleterID are set by the server from the caller.
	CreatorID   *uuid.UUID
	CompleterID *uuid.UUID
	UpdatedAt   time.Time
	Version     int64
}
