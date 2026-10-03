package locale_test

import (
	"errors"
	"strings"
	"testing"

	"github.com/mickamy/LocateDo/tools/l10n/internal/locale"
	"github.com/mickamy/LocateDo/tools/l10n/internal/template"
)

func TestParseYAML(t *testing.T) {
	t.Parallel()

	src := `
greeting: "Hello!"
hello: "Hello, {name}!"
items_count:
  plural:
    one: "You have {count} item."
    other: "You have {count} items."
total_price: "Total: {price:number}"
user:
  not_found: "User not found."
  deleted: "User {name} has been deleted."
`
	c := mustParseYAML(t, src)
	if c.Lang != "en" {
		t.Errorf("Lang = %q, want %q", c.Lang, "en")
	}

	wantKeys := []string{"greeting", "hello", "items_count", "total_price", "user.not_found", "user.deleted"}
	if len(c.Entries) != len(wantKeys) {
		t.Errorf("len(Entries) = %d, want %d", len(c.Entries), len(wantKeys))
	}
	for _, key := range wantKeys {
		if _, ok := c.Entries[key]; !ok {
			t.Errorf("Entries[%q] is missing", key)
		}
	}

	deleted := c.Entries["user.deleted"]
	if got := render(deleted.Single, map[string]string{"name": "Alice"}); got != "User Alice has been deleted." {
		t.Errorf("user.deleted = %q", got)
	}
	if deleted.Line != 11 {
		t.Errorf("user.deleted Line = %d, want 11", deleted.Line)
	}

	plural := c.Entries["items_count"]
	if plural.Plural == nil {
		t.Fatal("items_count is not a plural entry")
	}
	if len(plural.Plural) != 2 {
		t.Errorf("len(items_count.Plural) = %d, want 2", len(plural.Plural))
	}
	if got := render(plural.Plural["one"], map[string]string{"count": "1"}); got != "You have 1 item." {
		t.Errorf("items_count.one = %q", got)
	}
	if plural.Line != 4 {
		t.Errorf("items_count Line = %d, want 4", plural.Line)
	}
	if c.Entries["greeting"].Plural != nil {
		t.Error("greeting should not be a plural entry")
	}
}

func TestParseYAML_empty(t *testing.T) {
	t.Parallel()

	for _, src := range []string{"", "# comment only\n", "{}\n"} {
		c := mustParseYAML(t, src)
		if len(c.Entries) != 0 {
			t.Errorf("ParseYAML(%q): len(Entries) = %d, want 0", src, len(c.Entries))
		}
	}
}

func TestParseYAML_aliases(t *testing.T) {
	t.Parallel()

	src := `
brand: &brand "MyApp"
welcome: *brand
plans: &plans
  free: "Free plan"
  pro: "Pro plan"
pricing: *plans
`
	c := mustParseYAML(t, src)
	if got := render(c.Entries["welcome"].Single, nil); got != "MyApp" {
		t.Errorf("welcome = %q, want %q", got, "MyApp")
	}
	if got := render(c.Entries["pricing.pro"].Single, nil); got != "Pro plan" {
		t.Errorf("pricing.pro = %q, want %q", got, "Pro plan")
	}
}

func TestParseYAML_pluralOtherOnly(t *testing.T) {
	t.Parallel()

	c := mustParseYAML(t, "items_count:\n  plural:\n    other: \"アイテムが{count}個あります。\"\n")
	entry := c.Entries["items_count"]
	if entry.Plural == nil {
		t.Fatal("items_count is not a plural entry")
	}
	got := render(entry.Plural["other"], map[string]string{"count": "5"})
	if got != "アイテムが5個あります。" {
		t.Errorf("items_count.other = %q", got)
	}
}

func TestParseYAML_categoryNamesAreOrdinaryKeys(t *testing.T) {
	t.Parallel()

	c := mustParseYAML(t, "errors:\n  other: \"boom\"\nstatus:\n  many: \"多数\"\n  active: \"有効\"\n")
	for _, key := range []string{"errors.other", "status.many", "status.active"} {
		entry, ok := c.Entries[key]
		if !ok {
			t.Errorf("Entries[%q] is missing", key)
			continue
		}
		if entry.Plural != nil {
			t.Errorf("Entries[%q] was read as a plural group", key)
		}
	}
}

func TestParseYAML_scalarPluralIsAnOrdinaryKey(t *testing.T) {
	t.Parallel()

	c := mustParseYAML(t, "modes:\n  plural: \"Plural\"\n  singular: \"Singular\"\n")
	if _, ok := c.Entries["modes.plural"]; !ok {
		t.Errorf("Entries[%q] is missing: %v", "modes.plural", c.Entries)
	}
}

func TestParseYAML_error(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		src  string
	}{
		{name: "top-level sequence", src: "- a\n- b\n"},
		{name: "top-level scalar", src: "\"hi\"\n"},
		{name: "int value", src: "greeting: 123\n"},
		{name: "bool value", src: "greeting: true\n"},
		{name: "null value", src: "greeting:\n"},
		{name: "sequence value", src: "greeting:\n  - a\n"},
		{name: "uppercase key", src: "Greeting: hi\n"},
		{name: "key with dot", src: "\"a.b\": hi\n"},
		{name: "key with hyphen", src: "a-b: hi\n"},
		{name: "key starting with digit", src: "1st: hi\n"},
		{name: "non-string key", src: "123: hi\n"},
		{name: "duplicate key", src: "a: x\nb: y\na: z\n"},
		{name: "duplicate nested key", src: "a:\n  b: x\n  b: y\n"},
		{name: "empty mapping", src: "user: {}\n"},
		{name: "invalid template", src: "greeting: \"Hello, {name\"\n"},
		{name: "plural without other", src: "items:\n  plural:\n    one: \"One\"\n"},
		{name: "plural sharing its mapping", src: "items:\n  plural:\n    other: \"x\"\n  custom: \"Custom\"\n"},
		{name: "non-category under plural", src: "items:\n  plural:\n    other: \"x\"\n    custom: \"y\"\n"},
		{name: "non-string key under plural", src: "items:\n  plural:\n    1: \"x\"\n    other: \"y\"\n"},
		{name: "duplicate category", src: "items:\n  plural:\n    other: \"x\"\n    other: \"y\"\n"},
		{name: "plural form not a string", src: "items:\n  plural:\n    other:\n      nested: \"x\"\n"},
		{name: "plural form int", src: "items:\n  plural:\n    other: 1\n"},
		{name: "plural count as number", src: "items:\n  plural:\n    other: \"{count:number} items\"\n"},
		{name: "unknown anchor", src: "a: *missing\n"},
		{name: "multiple documents", src: "a: \"1\"\n---\nb: \"2\"\n"},
		{name: "malformed second document", src: "a: \"1\"\n---\nb: [\n"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			if _, err := locale.ParseYAML("en", []byte(tt.src)); err == nil {
				t.Errorf("ParseYAML(%q) returned nil error", tt.src)
			}
		})
	}
}

func TestParseYAML_errorPosition(t *testing.T) {
	t.Parallel()

	_, err := locale.ParseYAML("en", []byte("a: \"x\"\nb: 1\n"))
	pe, ok := errors.AsType[*locale.Error](err)
	if !ok {
		t.Fatalf("error is %T, want *locale.Error", err)
	}
	if pe.Line != 2 {
		t.Errorf("Line = %d, want 2", pe.Line)
	}
	if !strings.Contains(pe.Msg, `key "b"`) {
		t.Errorf("Msg = %q, want it to name key b", pe.Msg)
	}
}

func mustParseYAML(t *testing.T, src string) locale.Catalog {
	t.Helper()
	c, err := locale.ParseYAML("en", []byte(src))
	if err != nil {
		t.Fatalf("ParseYAML() returned error: %v", err)
	}
	return c
}

func render(tmpl template.Template, args map[string]string) string {
	var b strings.Builder
	for _, seg := range tmpl.Segments() {
		if seg.Param == "" {
			b.WriteString(seg.Text)
			continue
		}
		b.WriteString(args[seg.Param])
	}
	return b.String()
}
