package usecase_test

import (
	"encoding/json"
	"testing"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/errors/aerrors"
	"github.com/mickamy/LocateDo/internal/feature/account/model"
	"github.com/mickamy/LocateDo/internal/feature/account/repository"
	"github.com/mickamy/LocateDo/internal/feature/account/usecase"
	hmodel "github.com/mickamy/LocateDo/internal/feature/household/model"
	"github.com/mickamy/LocateDo/internal/infra/apple"
	"github.com/mickamy/LocateDo/internal/outbox"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestDeleteAccount(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	infra, fake := fakedApple(d)
	lib := newLib()
	signedIn, err := usecase.NewSignInWithApple(infra, lib).Do(fixedClock(t), signInInput("apple-sub", ""))
	require.NoError(t, err)
	userID := signedIn.Session.UserID

	// act
	err = usecase.NewDeleteAccount(infra).Do(fixedClock(t), usecase.DeleteAccountInput{UserID: userID})

	// assert
	require.NoError(t, err)
	_, err = repository.NewUser(d.Reader).FindByIdentity(t.Context(), model.ProviderApple, "apple-sub")
	require.ErrorIs(t, err, aerrors.ErrNotFound)
	_, err = usecase.NewRefreshToken(infra, lib).Do(fixedClock(t), usecase.RefreshTokenInput{
		RefreshToken: signedIn.Session.RefreshToken,
	})
	require.ErrorIs(t, err, aerrors.ErrUnauthenticated)

	assert.Empty(t, fake.revokedTokens(), "Apple is not called inline")
	var kind string
	var payload []byte
	require.NoError(t, d.Writer.QueryRow(t.Context(), "SELECT kind, payload FROM outbox_messages").Scan(&kind, &payload))
	assert.Equal(t, string(outbox.KindRevokeAppleToken), kind)
	var revocation model.AppleRevocation
	require.NoError(t, json.Unmarshal(payload, &revocation))
	assert.Equal(t, userID, revocation.UserID)
	assert.Equal(t, apple.ClientApp, revocation.Client)
	plain, err := lib.Box.Open(revocation.SealedToken, userID[:])
	require.NoError(t, err)
	assert.Equal(t, "apple-refresh:auth-code", string(plain), "the message carries the token the cascade deletes")
}

func TestDeleteAccount_revokesWithTheServicesID(t *testing.T) {
	t.Parallel()

	// arrange: signed in last on Android, through the Services ID
	d := tdb.New(t)
	infra, _ := fakedApple(d)
	signedIn, err := usecase.NewSignInWithApple(infra, newLib()).Do(fixedClock(t), servicesSignInInput("apple-sub", ""))
	require.NoError(t, err)

	// act
	err = usecase.NewDeleteAccount(infra).Do(fixedClock(t), usecase.DeleteAccountInput{UserID: signedIn.Session.UserID})

	// assert
	require.NoError(t, err)
	var payload []byte
	require.NoError(t, d.Writer.QueryRow(t.Context(), "SELECT payload FROM outbox_messages").Scan(&payload))
	var revocation model.AppleRevocation
	require.NoError(t, json.Unmarshal(payload, &revocation))
	assert.Equal(t, apple.ClientServices, revocation.Client)
}

func TestDeleteAccount_ownerWithTheirOwnTodos(t *testing.T) {
	t.Parallel()

	// arrange: the owner added a to-do, completed it, and took it on
	d := tdb.New(t)
	h := d.Seeder.Household(t, hmodel.PlanFree)
	todoID := d.Seeder.CompletedTodo(t, h.ID, d.Seeder.Place(t, h.ID))
	_, err := d.Writer.Exec(t.Context(), "UPDATE todos SET creator_id = $1, assignee_id = $1 WHERE id = $2",
		h.OwnerID, todoID)
	require.NoError(t, err)
	_, err = d.Writer.Exec(t.Context(),
		"INSERT INTO todo_completions (todo_id, completer_id, completed_at) VALUES ($1, $2, now())", todoID, h.OwnerID)
	require.NoError(t, err)

	// act
	err = usecase.NewDeleteAccount(d.Infra()).Do(fixedClock(t), usecase.DeleteAccountInput{UserID: h.OwnerID})

	// assert
	require.NoError(t, err)
	var households int
	require.NoError(t, d.Writer.QueryRow(t.Context(), "SELECT count(*) FROM households").Scan(&households))
	assert.Zero(t, households)
}

func TestDeleteAccount_memberLeavesTheirTodosBehind(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	h := d.Seeder.Household(t, hmodel.PlanPro)
	member := d.Seeder.Member(t, h.ID)
	todoID := d.Seeder.Todo(t, h.ID, d.Seeder.Place(t, h.ID))
	_, err := d.Writer.Exec(t.Context(), "UPDATE todos SET creator_id = $1, assignee_id = $1 WHERE id = $2",
		member, todoID)
	require.NoError(t, err)
	before := d.Seeder.Version(t, h.ID)

	// act
	err = usecase.NewDeleteAccount(d.Infra()).Do(fixedClock(t), usecase.DeleteAccountInput{UserID: member})

	// assert
	require.NoError(t, err)
	var creator, assignee *uuid.UUID
	require.NoError(t, d.Writer.QueryRow(t.Context(),
		"SELECT creator_id, assignee_id FROM todos WHERE id = $1", todoID).Scan(&creator, &assignee))
	assert.Nil(t, creator)
	assert.Nil(t, assignee)
	assert.Greater(t, d.Seeder.Version(t, h.ID), before, "the other members sync the change")
}

func TestDeleteAccount_withoutAppleToken(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	userID := d.Seeder.User(t)

	// act
	err := usecase.NewDeleteAccount(d.Infra()).Do(fixedClock(t), usecase.DeleteAccountInput{UserID: userID})

	// assert
	require.NoError(t, err)
	var messages int
	require.NoError(t, d.Writer.QueryRow(t.Context(), "SELECT count(*) FROM outbox_messages").Scan(&messages))
	assert.Zero(t, messages, "nothing to revoke")
}

func TestDeleteAccount_unknownUser(t *testing.T) {
	t.Parallel()

	d := tdb.New(t)

	err := usecase.NewDeleteAccount(d.Infra()).Do(fixedClock(t), usecase.DeleteAccountInput{UserID: uuid.NewV7()})

	require.ErrorIs(t, err, aerrors.ErrNotFound)
}
