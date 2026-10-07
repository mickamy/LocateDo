package model

import (
	"slices"
	"time"
	"unicode/utf8"

	"golang.org/x/text/language"

	dmodel "github.com/mickamy/LocateDo/internal/feature/device/model"
	"github.com/mickamy/LocateDo/internal/messages"
)

const noticeTitleLength = 40

type CompletedTodo struct {
	Title       string
	CompletedAt time.Time
}

func (t CompletedTodo) noticeTitle() string {
	if utf8.RuneCountInString(t.Title) <= noticeTitleLength {
		return t.Title
	}
	return string([]rune(t.Title)[:noticeTitleLength-1]) + "…"
}

// CompletionNotice words the notice telling a to-do's creator that the
// completer checked off the to-dos, in the receiving device's language. It
// names the one completed first; todos must not be empty.
func CompletionNotice(lang dmodel.Language, completer string, todos []CompletedTodo) string {
	l := messages.Localizer(language.Make(string(lang)))
	if completer == "" {
		completer = l.Localize(messages.CompletionNoticeSomeone())
	}
	first := slices.MinFunc(todos, func(a, b CompletedTodo) int { return a.CompletedAt.Compare(b.CompletedAt) })
	title := first.noticeTitle()
	if len(todos) == 1 {
		return l.Localize(messages.CompletionNoticeOne(completer, title))
	}
	return l.Localize(messages.CompletionNoticeMany(completer, title, len(todos)-1))
}
