package model_test

import (
	"strings"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"

	dmodel "github.com/mickamy/LocateDo/internal/feature/device/model"
	"github.com/mickamy/LocateDo/internal/feature/todo/model"
)

func TestCompletionNotice(t *testing.T) {
	t.Parallel()

	at := time.Date(2026, 10, 7, 12, 0, 0, 0, time.UTC)
	milk := model.CompletedTodo{Title: "Milk", CompletedAt: at}
	eggs := model.CompletedTodo{Title: "Eggs", CompletedAt: at.Add(time.Minute)}
	bread := model.CompletedTodo{Title: "Bread", CompletedAt: at.Add(2 * time.Minute)}
	//nolint:gosmopolitan // the Japanese notice
	tests := []struct {
		name      string
		language  dmodel.Language
		completer string
		todos     []model.CompletedTodo
		want      string
	}{
		{name: "one, en", language: "en", completer: "Alex", todos: []model.CompletedTodo{milk},
			want: `Alex checked off "Milk"`},
		{name: "many, en, named by the first completed", language: "en", completer: "Alex",
			todos: []model.CompletedTodo{bread, milk, eggs}, want: `Alex checked off "Milk" and 2 more`},
		{name: "no name, en", language: "en", todos: []model.CompletedTodo{milk},
			want: `Someone in your household checked off "Milk"`},
		{name: "one, ja", language: "ja", completer: "太郎", todos: []model.CompletedTodo{milk},
			want: "太郎が「Milk」を完了しました"},
		{name: "many, ja", language: "ja", completer: "太郎", todos: []model.CompletedTodo{eggs, milk},
			want: "太郎が「Milk」ほか 1 件を完了しました"},
		{name: "no name, ja", language: "ja", todos: []model.CompletedTodo{milk},
			want: "家族が「Milk」を完了しました"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			// act
			got := model.CompletionNotice(tt.language, tt.completer, tt.todos)

			// assert
			assert.Equal(t, tt.want, got)
		})
	}
}

func TestCompletionNotice_shortensLongTitles(t *testing.T) {
	t.Parallel()

	// arrange
	long := model.CompletedTodo{Title: strings.Repeat("a", 41), CompletedAt: time.Now()}

	// act
	got := model.CompletionNotice("en", "Alex", []model.CompletedTodo{long})

	// assert
	assert.Equal(t, `Alex checked off "`+strings.Repeat("a", 39)+`…"`, got)
}
