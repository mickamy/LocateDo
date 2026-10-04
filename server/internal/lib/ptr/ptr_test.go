package ptr_test

import (
	"strconv"
	"testing"

	"github.com/stretchr/testify/assert"

	"github.com/mickamy/LocateDo/internal/lib/ptr"
)

func TestMap(t *testing.T) {
	t.Parallel()

	n := 42

	got := ptr.Map(&n, strconv.Itoa)

	assert.Equal(t, "42", *got)
}

func TestMap_nil(t *testing.T) {
	t.Parallel()

	got := ptr.Map(nil, strconv.Itoa)

	assert.Nil(t, got)
}
