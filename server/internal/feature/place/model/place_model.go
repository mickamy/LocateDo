package model

import "uuid"

type Place struct {
	ID          uuid.UUID
	HouseholdID uuid.UUID
	Name        string
	Lat         float64 `fake:"{float64range:-90,90}"`
	Lng         float64 `fake:"{float64range:-180,180}"`
	RadiusM     int32   `fake:"{number:50,500}"`
	CategoryID  *uuid.UUID
	SortOrder   int32
}
