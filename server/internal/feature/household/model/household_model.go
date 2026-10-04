package model

import (
	"time"
	"uuid"
)

const (
	MaxMembers       = 6
	MaxFreePlaces    = 3
	MaxFreeOpenTodos = 15
)

type Plan string

const (
	PlanFree Plan = "free"
	PlanPro  Plan = "pro"
)

func (p Plan) AllowsPlaces(n int) bool {
	return p == PlanPro || n <= MaxFreePlaces
}

func (p Plan) AllowsOpenTodos(n int) bool {
	return p == PlanPro || n <= MaxFreeOpenTodos
}

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
	DisplayName string
	JoinedAt    time.Time
	UpdatedAt   time.Time
	Version     int64
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
	Todos      []InitialTodo
}

type ImportCategory struct {
	ID         uuid.UUID
	BuiltinKey *string `map:"Builtin"`
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

type InitialTodo struct {
	Todo        ImportTodo
	CompletedAt *time.Time
}

type ImportTodo struct {
	ID         uuid.UUID
	PlaceID    uuid.UUID
	Title      string
	AssigneeID *uuid.UUID
}
