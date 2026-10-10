package model

import (
	"time"
	"uuid"
)

// PlaceEvent is the moment at a place a to-do is brought up.
type PlaceEvent string

const (
	PlaceEventArrival   PlaceEvent = "arrival"
	PlaceEventDeparture PlaceEvent = "departure"
)

// Trigger says when a to-do is brought up.
type Trigger struct {
	Event PlaceEvent `fake:"{randomstring:[arrival,departure]}"`
}

type Todo struct {
	ID          uuid.UUID
	HouseholdID uuid.UUID
	PlaceID     uuid.UUID
	Title       string
	Trigger     Trigger
	AssigneeID  *uuid.UUID
	CompletedAt *time.Time
	// CreatorID and CompleterID are set by the server from the caller.
	CreatorID   *uuid.UUID
	CompleterID *uuid.UUID
	UpdatedAt   time.Time
	Version     int64
}
