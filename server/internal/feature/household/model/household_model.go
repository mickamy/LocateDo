package model

import (
	"time"
	"uuid"
)

const MaxMembers = 6

type Plan string

const (
	PlanFree Plan = "free"
	PlanPro  Plan = "pro"
)

type Role string

const (
	RoleOwner  Role = "owner"
	RoleMember Role = "member"
)

type Household struct {
	ID        uuid.UUID
	OwnerID   uuid.UUID
	Plan      Plan
	CreatedAt time.Time
}

type Membership struct {
	HouseholdID uuid.UUID
	UserID      uuid.UUID
	Role        Role
	JoinedAt    time.Time
}

type Invite struct {
	ID          uuid.UUID
	HouseholdID uuid.UUID
	CreatedBy   uuid.UUID
	ExpiresAt   time.Time
}

const InviteTTL = 72 * time.Hour

// Contents is what a device built before signing in, imported with the
// household in one transaction.
type Contents struct {
	Categories []ImportCategory
	Places     []ImportPlace
	Todos      []ImportTodo
}

type ImportCategory struct {
	ID         uuid.UUID
	BuiltinKey *string
	Name       *string
	Icon       string
	Color      string
	SortOrder  int32
}

type ImportPlace struct {
	ID         uuid.UUID
	Name       string
	Lat        float64
	Lng        float64
	RadiusM    int32
	CategoryID *uuid.UUID
	SortOrder  int32
}

type ImportTodo struct {
	ID          uuid.UUID
	PlaceID     uuid.UUID
	Title       string
	AssigneeID  *uuid.UUID
	CompletedAt *time.Time
}
