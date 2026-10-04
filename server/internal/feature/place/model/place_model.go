package model

import "uuid"

type Place struct {
	ID          uuid.UUID
	HouseholdID uuid.UUID
	Name        string
	Lat         float64
	Lng         float64
	RadiusM     int32
	CategoryID  *uuid.UUID
	SortOrder   int32
}
