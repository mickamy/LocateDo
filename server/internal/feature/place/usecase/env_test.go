package usecase_test

import (
	"testing"

	"github.com/mickamy/LocateDo/internal/di"
	"github.com/mickamy/LocateDo/internal/feature/place/repository"
	"github.com/mickamy/LocateDo/internal/feature/place/usecase"
	"github.com/mickamy/LocateDo/test/tinfra"
	"github.com/mickamy/LocateDo/test/tseed"
)

type env struct {
	infra       di.Infra
	seed        tseed.Seeder
	places      repository.Place
	putPlace    *usecase.PutPlace
	deletePlace *usecase.DeletePlace
}

func newEnv(t *testing.T) *env {
	t.Helper()

	infra := tinfra.New(t)
	return &env{
		infra:       infra,
		seed:        tseed.New(infra.Writer),
		places:      repository.NewPlace(infra.Reader),
		putPlace:    usecase.NewPutPlace(infra),
		deletePlace: usecase.NewDeletePlace(infra),
	}
}
