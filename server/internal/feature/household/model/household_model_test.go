package model_test

import (
	"testing"

	"github.com/stretchr/testify/assert"

	"github.com/mickamy/LocateDo/internal/feature/household/model"
)

func TestPlan_limits(t *testing.T) {
	t.Parallel()

	assert.True(t, model.PlanFree.AllowsPlaces(model.MaxFreePlaces))
	assert.False(t, model.PlanFree.AllowsPlaces(model.MaxFreePlaces+1))
	assert.True(t, model.PlanPro.AllowsPlaces(model.MaxFreePlaces+1))

	assert.True(t, model.PlanFree.AllowsOpenTodos(model.MaxFreeOpenTodos))
	assert.False(t, model.PlanFree.AllowsOpenTodos(model.MaxFreeOpenTodos+1))
	assert.True(t, model.PlanPro.AllowsOpenTodos(model.MaxFreeOpenTodos+1))
}
