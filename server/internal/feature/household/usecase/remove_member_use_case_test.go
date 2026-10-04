package usecase_test

import (
	"testing"
	"uuid"

	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/household/usecase"
)

func TestRemoveMember(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		// arrange returns the caller and the user to remove.
		arrange func(t *testing.T, e *env, ownerID, memberID uuid.UUID) (uuid.UUID, uuid.UUID)
		want    error
	}{
		{
			name: "owner removes a member",
			arrange: func(_ *testing.T, _ *env, ownerID, memberID uuid.UUID) (uuid.UUID, uuid.UUID) {
				return ownerID, memberID
			},
		},
		{
			name: "member leaves",
			arrange: func(_ *testing.T, _ *env, _, memberID uuid.UUID) (uuid.UUID, uuid.UUID) {
				return memberID, memberID
			},
		},
		{
			name: "owner cannot leave",
			arrange: func(_ *testing.T, _ *env, ownerID, _ uuid.UUID) (uuid.UUID, uuid.UUID) {
				return ownerID, ownerID
			},
			want: aerrors.ErrPrecondition,
		},
		{
			name: "member cannot remove others",
			arrange: func(_ *testing.T, _ *env, ownerID, memberID uuid.UUID) (uuid.UUID, uuid.UUID) {
				return memberID, ownerID
			},
			want: aerrors.ErrPermissionDenied,
		},
		{
			name: "outsider",
			arrange: func(t *testing.T, e *env, _, memberID uuid.UUID) (uuid.UUID, uuid.UUID) {
				return e.seed.User(t), memberID
			},
			want: aerrors.ErrPermissionDenied,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			// arrange
			e := newEnv(t)
			h := e.seed.Household(t, model.PlanPro)
			memberID := e.seed.Member(t, h.ID)
			callerID, userID := tt.arrange(t, e, h.OwnerID, memberID)

			// act
			err := e.removeMember.Do(e.ctx, usecase.RemoveMemberInput{
				CallerID: callerID, HouseholdID: h.ID, UserID: userID,
			})

			// assert
			if tt.want != nil {
				require.ErrorIs(t, err, tt.want)
				return
			}
			require.NoError(t, err)
			_, err = e.memberships.FindByUser(t.Context(), userID)
			require.ErrorIs(t, err, aerrors.ErrNotFound)
		})
	}
}
