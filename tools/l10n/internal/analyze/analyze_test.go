package analyze_test

import (
	"reflect"
	"strings"
	"testing"

	"github.com/mickamy/LocateDo/tools/l10n/internal/analyze"
	"github.com/mickamy/LocateDo/tools/l10n/internal/locale"
	"github.com/mickamy/LocateDo/tools/l10n/internal/template"
)

const enSrc = `
greeting: "Hello!"
todo:
  assigned: "{assignee} took {count:int} items from {owner}"
  remaining:
    plural:
      one: "{count} item left at {place}"
      other: "{count} items left at {place}"
`

const jaSrc = `
greeting: "こんにちは！"
todo:
  assigned: "{owner} の {count} 件を {assignee} が担当"
  remaining:
    plural:
      other: "{place} に残り {count} 件"
`

func TestAnalyze(t *testing.T) {
	t.Parallel()

	meta, err := locale.ParseMeta([]byte("greeting:\n  comment: \"Shown on launch\"\n"))
	if err != nil {
		t.Fatal(err)
	}
	model, diags := analyze.Analyze(catalogs(t, enSrc, jaSrc), "en", meta)
	if len(diags) != 0 {
		t.Fatalf("Analyze() diags = %v, want none", diags)
	}

	if model.DefaultLang != "en" {
		t.Errorf("DefaultLang = %q, want en", model.DefaultLang)
	}
	if want := []string{"en", "ja"}; !reflect.DeepEqual(model.Langs, want) {
		t.Errorf("Langs = %v, want %v", model.Langs, want)
	}
	if got := model.Meta.Comment("greeting"); got != "Shown on launch" {
		t.Errorf("Meta.Comment(greeting) = %q", got)
	}

	keys := make([]string, 0, len(model.Messages))
	for _, msg := range model.Messages {
		keys = append(keys, msg.Key)
	}
	if want := []string{"greeting", "todo.assigned", "todo.remaining"}; !reflect.DeepEqual(keys, want) {
		t.Fatalf("message keys = %v, want %v", keys, want)
	}

	assigned := model.Messages[1]
	if assigned.Plural {
		t.Error("todo.assigned is marked plural")
	}
	wantParams := []template.Param{
		{Name: "assignee", Kind: template.KindString},
		{Name: "count", Kind: template.KindInt},
		{Name: "owner", Kind: template.KindString},
	}
	if !reflect.DeepEqual(assigned.Params, wantParams) {
		t.Errorf("todo.assigned Params = %v, want %v", assigned.Params, wantParams)
	}
	if len(assigned.Translations) != 2 {
		t.Errorf("todo.assigned has %d translations, want 2", len(assigned.Translations))
	}

	remaining := model.Messages[2]
	if !remaining.Plural {
		t.Error("todo.remaining is not marked plural")
	}
	wantParams = []template.Param{
		{Name: "count", Kind: template.KindInt},
		{Name: "place", Kind: template.KindString},
	}
	if !reflect.DeepEqual(remaining.Params, wantParams) {
		t.Errorf("todo.remaining Params = %v, want %v", remaining.Params, wantParams)
	}
	if _, ok := remaining.Translations["ja"].Plural["other"]; !ok {
		t.Error("todo.remaining ja translation is missing the other form")
	}
}

func TestMessage_Placeholder(t *testing.T) {
	t.Parallel()

	model, diags := analyze.Analyze(catalogs(t, enSrc, jaSrc), "en", locale.Meta{})
	if analyze.HasErrors(diags) {
		t.Fatalf("Analyze() diags = %v", diags)
	}
	assigned := model.Messages[1]

	tests := []struct {
		name string
		pos  int
		kind template.Kind
	}{
		{name: "assignee", pos: 1, kind: template.KindString},
		{name: "count", pos: 2, kind: template.KindInt},
		{name: "owner", pos: 3, kind: template.KindString},
		{name: "missing", pos: 0, kind: template.KindString},
	}
	for _, tt := range tests {
		pos, kind := assigned.Placeholder(tt.name)
		if pos != tt.pos || kind != tt.kind {
			t.Errorf("Placeholder(%q) = (%d, %v), want (%d, %v)", tt.name, pos, kind, tt.pos, tt.kind)
		}
	}
}

func TestAnalyze_defaultMissing(t *testing.T) {
	t.Parallel()

	_, diags := analyze.Analyze(catalogs(t, enSrc, jaSrc), "fr", locale.Meta{})
	if !analyze.HasErrors(diags) {
		t.Fatalf("Analyze() diags = %v, want an error", diags)
	}
	if !strings.Contains(diags[0].Msg, "available: en, ja") {
		t.Errorf("Msg = %q, want it to list the available locales", diags[0].Msg)
	}
}

func TestAnalyze_warnings(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		en   string
		ja   string
		want string
	}{
		{
			name: "missing translation",
			en:   "a: \"x\"\nb: \"y\"\n",
			ja:   "a: \"x\"\n",
			want: `locale ja: missing key "b"`,
		},
		{
			name: "missing plural form in default",
			en:   "items:\n  plural:\n    other: \"{count} items\"\n",
			ja:   "items:\n  plural:\n    other: \"{count} 件\"\n",
			want: `locale en: key "items": missing plural forms one`,
		},
		{
			name: "missing plural form in translation",
			en:   "items:\n  plural:\n    one: \"{count} item\"\n    other: \"{count} items\"\n",
			ja:   "items:\n  plural:\n    other: \"{count} 件\"\n",
			want: "",
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			model, diags := analyze.Analyze(catalogs(t, tt.en, tt.ja), "en", locale.Meta{})
			if analyze.HasErrors(diags) {
				t.Fatalf("Analyze() diags = %v, want warnings only", diags)
			}
			if len(model.Messages) == 0 {
				t.Error("Analyze() returned an empty model alongside warnings")
			}
			if tt.want == "" {
				if len(diags) != 0 {
					t.Errorf("Analyze() diags = %v, want none", diags)
				}
				return
			}
			if len(diags) != 1 || diags[0].Severity != analyze.Warning || !strings.Contains(diags[0].Msg, tt.want) {
				t.Errorf("Analyze() diags = %v, want one warning containing %q", diags, tt.want)
			}
		})
	}
}

func TestAnalyze_unknownPluralRules(t *testing.T) {
	t.Parallel()

	fr, err := locale.ParseYAML("fr", []byte("items:\n  plural:\n    other: \"{count} articles\"\n"))
	if err != nil {
		t.Fatal(err)
	}
	cats := append(catalogs(t, "items:\n  plural:\n    one: \"{count} item\"\n    other: \"{count} items\"\n"), fr)
	_, diags := analyze.Analyze(cats, "en", locale.Meta{})
	if analyze.HasErrors(diags) {
		t.Fatalf("Analyze() diags = %v, want warnings only", diags)
	}
	if len(diags) != 1 || !strings.Contains(diags[0].Msg, "plural rules for this language are unknown") {
		t.Errorf("Analyze() diags = %v, want one unknown-rules warning", diags)
	}
}

func TestAnalyze_errors(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		en   string
		ja   string
		meta string
		want string
	}{
		{
			name: "unknown key in translation",
			en:   "a: \"x\"\n",
			ja:   "a: \"x\"\nb: \"y\"\n",
			want: `key "b" does not exist in the default locale`,
		},
		{
			name: "plural shape differs",
			en:   "items: \"{count:int} items\"\n",
			ja:   "items:\n  plural:\n    other: \"{count} 件\"\n",
			want: "plural shape differs",
		},
		{
			name: "unknown parameter in translation",
			en:   "hello: \"Hello, {name}!\"\n",
			ja:   "hello: \"{name} さん、{title}\"\n",
			want: `parameter "title" does not exist`,
		},
		{
			name: "kind differs",
			en:   "total: \"{n:int} items\"\n",
			ja:   "total: \"{n:number} 件\"\n",
			want: `parameter "n" is number, but the default locale has int`,
		},
		{
			name: "kind conflict inside default plural",
			en:   "items:\n  plural:\n    one: \"{n:int} item\"\n    other: \"{n:number} items\"\n",
			ja:   "items:\n  plural:\n    other: \"{n} 件\"\n",
			want: "in one plural form and number in another",
		},
		{
			name: "meta key unknown",
			en:   "a: \"x\"\n",
			ja:   "a: \"x\"\n",
			meta: "b:\n  comment: \"x\"\n",
			want: `meta: key "b" does not exist`,
		},
		{
			name: "android resource name collision",
			en:   "a:\n  b_c: \"x\"\na_b:\n  c: \"y\"\n",
			ja:   "a:\n  b_c: \"x\"\na_b:\n  c: \"y\"\n",
			want: `both map to Android resource name "a_b_c"`,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			meta, err := locale.ParseMeta([]byte(tt.meta))
			if err != nil {
				t.Fatal(err)
			}
			model, diags := analyze.Analyze(catalogs(t, tt.en, tt.ja), "en", meta)
			if !analyze.HasErrors(diags) {
				t.Fatalf("Analyze() diags = %v, want an error", diags)
			}
			if len(model.Messages) != 0 {
				t.Error("Analyze() returned a model alongside errors")
			}
			found := false
			for _, d := range diags {
				if d.Severity == analyze.Error && strings.Contains(d.Msg, tt.want) {
					found = true
				}
			}
			if !found {
				t.Errorf("Analyze() diags = %v, want an error containing %q", diags, tt.want)
			}
		})
	}
}

func TestAnalyze_bareTranslationKeepsDefaultKind(t *testing.T) {
	t.Parallel()

	model, diags := analyze.Analyze(catalogs(t, "total: \"{n:int} items\"\n", "total: \"{n} 件\"\n"), "en", locale.Meta{})
	if len(diags) != 0 {
		t.Fatalf("Analyze() diags = %v, want none", diags)
	}
	pos, kind := model.Messages[0].Placeholder("n")
	if pos != 1 || kind != template.KindInt {
		t.Errorf("Placeholder(n) = (%d, %v), want (1, int)", pos, kind)
	}
}

func TestDiag_String(t *testing.T) {
	t.Parallel()

	tests := []struct {
		diag analyze.Diag
		want string
	}{
		{diag: analyze.Diag{Severity: analyze.Error, File: "en.yaml", Line: 3, Msg: "boom"}, want: "en.yaml:3: error: boom"},
		{diag: analyze.Diag{Severity: analyze.Warning, File: "ja.yaml", Msg: "hmm"}, want: "ja.yaml: warning: hmm"},
		{diag: analyze.Diag{Severity: analyze.Error, Msg: "boom"}, want: "error: boom"},
	}
	for _, tt := range tests {
		if got := tt.diag.String(); got != tt.want {
			t.Errorf("String() = %q, want %q", got, tt.want)
		}
	}
}

func TestAndroidResourceName(t *testing.T) {
	t.Parallel()

	if got := analyze.AndroidResourceName("place.add.title"); got != "place_add_title" {
		t.Errorf("AndroidResourceName() = %q, want place_add_title", got)
	}
}

func catalogs(t *testing.T, en string, others ...string) []locale.Catalog {
	t.Helper()
	enCatalog, err := locale.ParseYAML("en", []byte(en))
	if err != nil {
		t.Fatal(err)
	}
	enCatalog.File = "en.yaml"
	out := make([]locale.Catalog, 0, 1+len(others))
	out = append(out, enCatalog)
	for _, src := range others {
		ja, err := locale.ParseYAML("ja", []byte(src))
		if err != nil {
			t.Fatal(err)
		}
		ja.File = "ja.yaml"
		out = append(out, ja)
	}
	return out
}
