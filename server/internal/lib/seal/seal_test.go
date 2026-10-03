package seal_test

import (
	"bytes"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mickamy/LocateDo/internal/lib/seal"
)

var (
	key      = bytes.Repeat([]byte{1}, 32)
	otherKey = bytes.Repeat([]byte{2}, 32)
	owner    = []byte("user-a")
)

func TestNewBox_keyLength(t *testing.T) {
	t.Parallel()

	for _, n := range []int{0, 16, 31, 33} {
		_, err := seal.NewBox(make([]byte, n))
		require.ErrorIs(t, err, seal.ErrKeyLength, "len %d", n)
	}
}

func TestBox_roundTrip(t *testing.T) {
	t.Parallel()

	// arrange
	box := mustBox(t, key)
	plaintext := []byte("apple-refresh-token")

	// act
	sealed := box.Seal(plaintext, owner)
	got, err := box.Open(sealed, owner)

	// assert
	require.NoError(t, err)
	assert.Equal(t, plaintext, got)
	assert.NotContains(t, string(sealed), string(plaintext))
}

func TestBox_Seal_nonceIsRandom(t *testing.T) {
	t.Parallel()

	box := mustBox(t, key)

	a := box.Seal([]byte("same"), owner)
	b := box.Seal([]byte("same"), owner)

	assert.NotEqual(t, a, b)
}

func TestBox_Open_rejects(t *testing.T) {
	t.Parallel()

	box := mustBox(t, key)
	sealed := box.Seal([]byte("apple-refresh-token"), owner)
	tampered := bytes.Clone(sealed)
	tampered[len(tampered)-1] ^= 0xff

	tests := []struct {
		name   string
		box    seal.Box
		sealed []byte
		ad     []byte
	}{
		{name: "other owner", box: box, sealed: sealed, ad: []byte("user-b")},
		{name: "other key", box: mustBox(t, otherKey), sealed: sealed, ad: owner},
		{name: "tampered", box: box, sealed: tampered, ad: owner},
		{name: "too short", box: box, sealed: sealed[:10], ad: owner},
		{name: "empty", box: box, sealed: nil, ad: owner},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			_, err := tt.box.Open(tt.sealed, tt.ad)

			require.ErrorIs(t, err, seal.ErrOpen)
		})
	}
}

func mustBox(t *testing.T, key []byte) seal.Box {
	t.Helper()

	box, err := seal.NewBox(key)
	require.NoError(t, err)
	return box
}
