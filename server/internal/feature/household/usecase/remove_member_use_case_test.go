package usecase_test

import (
	"testing"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/feature/household/repository"
	"github.com/mickamy/LocateDo/internal/feature/household/usecase"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestRemoveMember(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		// arrange returns the caller and the user to remove.
		arrange func(t *testing.T, d tdb.DB, ownerID, memberID uuid.UUID) (uuid.UUID, uuid.UUID)
		want    error
	}{
		{
			name: "owner removes a member",
			arrange: func(_ *testing.T, _ tdb.DB, ownerID, memberID uuid.UUID) (uuid.UUID, uuid.UUID) {
				return ownerID, memberID
			},
		},
		{
			name: "member leaves",
			arrange: func(_ *testing.T, _ tdb.DB, _, memberID uuid.UUID) (uuid.UUID, uuid.UUID) {
				return memberID, memberID
			},
		},
		{
			name: "owner cannot leave",
			arrange: func(_ *testing.T, _ tdb.DB, ownerID, _ uuid.UUID) (uuid.UUID, uuid.UUID) {
				return ownerID, ownerID
			},
			want: aerrors.ErrPrecondition,
		},
		{
			name: "member cannot remove others",
			arrange: func(_ *testing.T, _ tdb.DB, ownerID, memberID uuid.UUID) (uuid.UUID, uuid.UUID) {
				return memberID, ownerID
			},
			want: aerrors.ErrPermissionDenied,
		},
		{
			name: "outsider",
			arrange: func(t *testing.T, d tdb.DB, _, memberID uuid.UUID) (uuid.UUID, uuid.UUID) {
				return d.Seeder.User(t), memberID
			},
			want: aerrors.ErrPermissionDenied,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			// arrange
			d := tdb.New(t)
			h := d.Seeder.Household(t, model.PlanPro)
			memberID := d.Seeder.Member(t, h.ID)
			callerID, userID := tt.arrange(t, d, h.OwnerID, memberID)

			// act
			err := usecase.NewRemoveMember(d.Infra()).Do(fixedClock(t), usecase.RemoveMemberInput{
				CallerID: callerID, HouseholdID: h.ID, UserID: userID,
			})

			// assert
			if tt.want != nil {
				require.ErrorIs(t, err, tt.want)
				return
			}
			require.NoError(t, err)
			_, err = repository.NewMembership(d.Reader).FindByUser(t.Context(), userID)
			require.ErrorIs(t, err, aerrors.ErrNotFound)
		})
	}
}

func TestRemoveMember_releasesAssignments(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	h := d.Seeder.Household(t, model.PlanPro)
	memberID := d.Seeder.Member(t, h.ID)
	placeID := d.Seeder.Place(t, h.ID)
	theirs := d.Seeder.Todo(t, h.ID, placeID)
	ownersOwn := d.Seeder.Todo(t, h.ID, placeID)
	_, err := d.Writer.Exec(t.Context(), "UPDATE todos SET assignee_id = $1 WHERE id = $2", memberID, theirs)
	require.NoError(t, err)
	_, err = d.Writer.Exec(t.Context(), "UPDATE todos SET assignee_id = $1 WHERE id = $2", h.OwnerID, ownersOwn)
	require.NoError(t, err)

	// act
	err = usecase.NewRemoveMember(d.Infra()).Do(fixedClock(t), usecase.RemoveMemberInput{
		CallerID: h.OwnerID, HouseholdID: h.ID, UserID: memberID,
	})

	// assert
	require.NoError(t, err)
	assert.Nil(t, assignee(t, d, theirs), "the removed member's todos go back to anyone")
	assert.Equal(t, &h.OwnerID, assignee(t, d, ownersOwn), "other assignments stay")
}

func assignee(t *testing.T, d tdb.DB, todoID uuid.UUID) *uuid.UUID {
	t.Helper()

	var id *uuid.UUID
	require.NoError(t, d.Writer.QueryRow(t.Context(), "SELECT assignee_id FROM todos WHERE id = $1", todoID).Scan(&id))
	return id
}
