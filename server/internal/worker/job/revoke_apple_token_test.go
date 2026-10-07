package job_test

import (
	"context"
	"encoding/json"
	"errors"
	"testing"
	"time"
	"uuid"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/feature/account/model"
	"github.com/mickamy/LocateDo/internal/infra/apple"
	"github.com/mickamy/LocateDo/internal/infra/storage/tx"
	"github.com/mickamy/LocateDo/internal/outbox"
	"github.com/mickamy/LocateDo/internal/worker"
	"github.com/mickamy/LocateDo/internal/worker/job"
	"github.com/mickamy/LocateDo/test/tdb"
)

func TestRevokeAppleToken_deliversThroughTheConsumer(t *testing.T) {
	t.Parallel()

	// arrange: the message a deleted account leaves behind
	d := tdb.New(t)
	fake := &fakeApple{}
	infra := d.Infra()
	infra.Apple = fake
	lib := di.MustNewLib(di.NewConfig())
	userID := uuid.NewV7()
	payload, err := json.Marshal(model.AppleRevocation{
		UserID:      userID,
		SealedToken: lib.Box.Seal([]byte("apple-refresh:abc"), userID[:]),
	})
	require.NoError(t, err)
	messages := outbox.NewRepository(d.Reader)
	d.InTx(t, func(tx tx.Tx) {
		require.NoError(t, messages.Bind(tx).Enqueue(t.Context(), outbox.Message{
			Kind: outbox.KindRevokeAppleToken, Payload: payload, RunAt: time.Now(),
		}))
	})
	consumer := worker.NewConsumer(d.Transactor, messages, worker.Handlers{
		outbox.KindRevokeAppleToken: job.NewRevokeAppleToken(infra, lib),
	})

	// act
	delivered, err := consumer.Step(t.Context())

	// assert
	require.NoError(t, err)
	assert.True(t, delivered)
	assert.Equal(t, []string{"apple-refresh:abc"}, fake.revoked)
}

func TestRevokeAppleToken_Handle_rejectsAGarbledPayload(t *testing.T) {
	t.Parallel()

	// arrange
	d := tdb.New(t)
	infra := d.Infra()
	infra.Apple = &fakeApple{}
	handler := job.NewRevokeAppleToken(infra, di.MustNewLib(di.NewConfig()))

	// act
	err := handler.Handle(t.Context(), outbox.Message{Kind: outbox.KindRevokeAppleToken, Payload: []byte("{")})

	// assert
	require.Error(t, err)
}

type fakeApple struct {
	revoked []string
}

var _ apple.Auth = (*fakeApple)(nil)

func (*fakeApple) VerifyIdentityToken(context.Context, string, string, time.Time) (apple.Identity, error) {
	return apple.Identity{}, errors.New("not used")
}

func (*fakeApple) VerifyWebIdentityToken(context.Context, string, string, time.Time) (apple.Identity, error) {
	return apple.Identity{}, errors.New("not used")
}

func (*fakeApple) ExchangeCode(context.Context, string, time.Time) (string, error) {
	return "", errors.New("not used")
}

func (f *fakeApple) Revoke(_ context.Context, refreshToken string, _ time.Time) error {
	f.revoked = append(f.revoked, refreshToken)
	return nil
}
