package template_test

import (
	"reflect"
	"strings"
	"testing"

	"github.com/mickamy/LocateDo/tools/l10n/internal/template"
)

func TestParse(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name   string
		src    string
		args   map[string]string
		want   string
		params []template.Param
	}{
		{
			name: "empty",
			src:  "",
			want: "",
		},
		{
			name: "literal only",
			src:  "Hello!",
			want: "Hello!",
		},
		{
			name:   "single placeholder",
			src:    "Hello, {name}!",
			args:   map[string]string{"name": "World"},
			want:   "Hello, World!",
			params: []template.Param{{Name: "name", Kind: template.KindString}},
		},
		{
			name: "explicit kinds",
			src:  "{count:int} items cost {price:number}",
			args: map[string]string{"count": "3", "price": "9.99"},
			want: "3 items cost 9.99",
			params: []template.Param{
				{Name: "count", Kind: template.KindInt},
				{Name: "price", Kind: template.KindNumber},
			},
		},
		{
			name:   "explicit string kind",
			src:    "Hello, {name:string}!",
			args:   map[string]string{"name": "World"},
			want:   "Hello, World!",
			params: []template.Param{{Name: "name", Kind: template.KindString}},
		},
		{
			name:   "bare occurrence inherits explicit kind",
			src:    "{price:number} and {price}",
			args:   map[string]string{"price": "10"},
			want:   "10 and 10",
			params: []template.Param{{Name: "price", Kind: template.KindNumber}},
		},
		{
			name:   "explicit kind after bare occurrence",
			src:    "{price} and {price:number}",
			args:   map[string]string{"price": "10"},
			want:   "10 and 10",
			params: []template.Param{{Name: "price", Kind: template.KindNumber}},
		},
		{
			name:   "escaped braces",
			src:    "{{name}} is {name}",
			args:   map[string]string{"name": "Alice"},
			want:   "{name} is Alice",
			params: []template.Param{{Name: "name", Kind: template.KindString}},
		},
		{
			name:   "multibyte literals",
			src:    "こんにちは、{name}さん！",
			args:   map[string]string{"name": "太郎"},
			want:   "こんにちは、太郎さん！",
			params: []template.Param{{Name: "name", Kind: template.KindString}},
		},
		{
			name: "adjacent placeholders",
			src:  "{a}{b}",
			args: map[string]string{"a": "1", "b": "2"},
			want: "12",
			params: []template.Param{
				{Name: "a", Kind: template.KindString},
				{Name: "b", Kind: template.KindString},
			},
		},
		{
			name:   "percent is literal",
			src:    "{percent:int}% done",
			args:   map[string]string{"percent": "50"},
			want:   "50% done",
			params: []template.Param{{Name: "percent", Kind: template.KindInt}},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			tmpl, err := template.Parse(tt.src)
			if err != nil {
				t.Fatalf("Parse(%q) returned error: %v", tt.src, err)
			}
			if got := render(tmpl, tt.args); got != tt.want {
				t.Errorf("render = %q, want %q", got, tt.want)
			}
			if params := tmpl.Params(); !reflect.DeepEqual(params, tt.params) {
				t.Errorf("Params() = %v, want %v", params, tt.params)
			}
		})
	}
}

func TestParse_error(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		src  string
	}{
		{name: "unclosed placeholder", src: "Hello, {name"},
		{name: "unmatched closing brace", src: "Hello }"},
		{name: "empty name", src: "{}"},
		{name: "name starting with digit", src: "{9lives}"},
		{name: "name starting with underscore", src: "{_x}"},
		{name: "uppercase name", src: "{Name}"},
		{name: "name with space", src: "{first name}"},
		{name: "unknown kind", src: "{price:float}"},
		{name: "empty kind", src: "{price:}"},
		{name: "conflicting kinds", src: "{x:int} {x:number}"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			if _, err := template.Parse(tt.src); err == nil {
				t.Errorf("Parse(%q) returned nil error", tt.src)
			}
		})
	}
}

func TestTemplate_Segments(t *testing.T) {
	t.Parallel()

	tmpl, err := template.Parse("{{name}} is {name}, {count:int} times")
	if err != nil {
		t.Fatal(err)
	}
	want := []template.Segment{
		{Text: "{name} is "},
		{Param: "name", Kind: template.KindString},
		{Text: ", "},
		{Param: "count", Kind: template.KindInt},
		{Text: " times"},
	}
	if got := tmpl.Segments(); !reflect.DeepEqual(got, want) {
		t.Errorf("Segments() = %v, want %v", got, want)
	}
}

func TestTemplate_Segments_bareInheritsKind(t *testing.T) {
	t.Parallel()

	tmpl, err := template.Parse("{n} of {n:int}")
	if err != nil {
		t.Fatal(err)
	}
	for _, seg := range tmpl.Segments() {
		if seg.Param == "n" && seg.Kind != template.KindInt {
			t.Errorf("segment %+v has Kind %v, want %v", seg, seg.Kind, template.KindInt)
		}
	}
}

func TestTemplate_Params_returnsCopy(t *testing.T) {
	t.Parallel()

	tmpl, err := template.Parse("Hello, {name}!")
	if err != nil {
		t.Fatal(err)
	}
	tmpl.Params()[0].Kind = template.KindNumber
	if got := tmpl.Params()[0].Kind; got != template.KindString {
		t.Errorf("mutating the returned slice changed internal state: Kind = %v", got)
	}
}

func TestTemplate_Explicit(t *testing.T) {
	t.Parallel()

	tmpl, err := template.Parse("{name} costs {price:number}")
	if err != nil {
		t.Fatal(err)
	}
	if tmpl.Explicit("name") {
		t.Error(`Explicit("name") = true, want false`)
	}
	if !tmpl.Explicit("price") {
		t.Error(`Explicit("price") = false, want true`)
	}
	if tmpl.Explicit("missing") {
		t.Error(`Explicit("missing") = true, want false`)
	}
}

func TestKind_String(t *testing.T) {
	t.Parallel()

	tests := []struct {
		kind template.Kind
		want string
	}{
		{kind: template.KindString, want: "string"},
		{kind: template.KindInt, want: "int"},
		{kind: template.KindNumber, want: "number"},
		{kind: template.Kind(99), want: "Kind(99)"},
	}
	for _, tt := range tests {
		if got := tt.kind.String(); got != tt.want {
			t.Errorf("Kind(%d).String() = %q, want %q", int(tt.kind), got, tt.want)
		}
	}
}

func TestValidName(t *testing.T) {
	t.Parallel()

	for _, s := range []string{"a", "name", "first_name", "item2", "a_1_b"} {
		if !template.ValidName(s) {
			t.Errorf("ValidName(%q) = false, want true", s)
		}
	}
	for _, s := range []string{"", "Name", "_x", "9lives", "first name", "a.b", "a-b", "名前"} {
		if template.ValidName(s) {
			t.Errorf("ValidName(%q) = true, want false", s)
		}
	}
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
