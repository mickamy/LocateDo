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
