package locale_test

import (
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"

	"github.com/mickamy/LocateDo/tools/l10n/internal/locale"
	"github.com/mickamy/LocateDo/tools/l10n/internal/template"
)

func TestParseFile(t *testing.T) {
	t.Parallel()

	dir := t.TempDir()
	writeFile(t, filepath.Join(dir, "en.yaml"), "greeting: \"Hello!\"\n")
	writeFile(t, filepath.Join(dir, "ja.yml"), "greeting: \"こんにちは！\"\n")
	writeFile(t, filepath.Join(dir, "de.YAML"), "greeting: \"Hallo!\"\n")

	tests := []struct {
		file string
		lang string
		want string
	}{
		{file: "en.yaml", lang: "en", want: "Hello!"},
		{file: "ja.yml", lang: "ja", want: "こんにちは！"},
		{file: "de.YAML", lang: "de", want: "Hallo!"},
	}
	for _, tt := range tests {
		path := filepath.Join(dir, tt.file)
		c, err := locale.ParseFile(path)
		if err != nil {
			t.Errorf("ParseFile(%s) returned error: %v", tt.file, err)
			continue
		}
		if c.Lang != tt.lang {
			t.Errorf("ParseFile(%s).Lang = %q, want %q", tt.file, c.Lang, tt.lang)
		}
		if c.File != path {
			t.Errorf("ParseFile(%s).File = %q, want %q", tt.file, c.File, path)
		}
		if got := render(c.Entries["greeting"].Single, nil); got != tt.want {
			t.Errorf("ParseFile(%s) greeting = %q, want %q", tt.file, got, tt.want)
		}
	}
}

func TestParseFile_error(t *testing.T) {
	t.Parallel()

	dir := t.TempDir()
	writeFile(t, filepath.Join(dir, "en.txt"), "greeting: hi\n")
	writeFile(t, filepath.Join(dir, "messages.yaml"), "greeting: hi\n")
	writeFile(t, filepath.Join(dir, "fr.yaml"), "greeting: \"ok\"\nbad: 1\n")

	for _, name := range []string{"en.txt", "messages.yaml", "es.yaml"} {
		if _, err := locale.ParseFile(filepath.Join(dir, name)); err == nil {
			t.Errorf("ParseFile(%s) returned nil error", name)
		}
	}

	path := filepath.Join(dir, "fr.yaml")
	_, err := locale.ParseFile(path)
	if err == nil {
		t.Fatal("ParseFile(fr.yaml) returned nil error")
	}
	if want := path + ":2: "; !strings.HasPrefix(err.Error(), want) {
		t.Errorf("error = %q, want prefix %q", err.Error(), want)
	}
}

func TestLoadDir(t *testing.T) {
	t.Parallel()

	dir := t.TempDir()
	writeFile(t, filepath.Join(dir, "ja.yml"), "greeting: \"こんにちは！\"\n")
	writeFile(t, filepath.Join(dir, "en.yaml"), "greeting: \"Hello!\"\n")
	writeFile(t, filepath.Join(dir, "meta.yaml"), "greeting:\n  comment: \"Shown on launch\"\n")
	writeFile(t, filepath.Join(dir, "README.md"), "# strings\n")
	writeFile(t, filepath.Join(dir, ".draft.yaml"), "- not a locale\n")
	if err := os.Mkdir(filepath.Join(dir, "fr"), 0o750); err != nil {
		t.Fatal(err)
	}

	catalogs, err := locale.LoadDir(dir)
	if err != nil {
		t.Fatalf("LoadDir() returned error: %v", err)
	}
	langs := make([]string, 0, len(catalogs))
	for _, c := range catalogs {
		langs = append(langs, c.Lang)
	}
	if want := []string{"en", "ja"}; !reflect.DeepEqual(langs, want) {
		t.Errorf("LoadDir() languages = %v, want %v", langs, want)
	}
}

func TestLoadDir_error(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name  string
		files map[string]string
	}{
		{name: "empty", files: map[string]string{}},
		{name: "only meta", files: map[string]string{"meta.yaml": "a:\n  comment: \"x\"\n"}},
		{name: "duplicate language", files: map[string]string{"en.yaml": "a: \"x\"\n", "en.yml": "a: \"y\"\n"}},
		{name: "non-language yaml", files: map[string]string{"en.yaml": "a: \"x\"\n", "strings.yaml": "a: \"y\"\n"}},
		{name: "broken file", files: map[string]string{"en.yaml": "a: 1\n"}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			dir := t.TempDir()
			for name, content := range tt.files {
				writeFile(t, filepath.Join(dir, name), content)
			}
			if _, err := locale.LoadDir(dir); err == nil {
				t.Error("LoadDir() returned nil error")
			}
		})
	}

	if _, err := locale.LoadDir(filepath.Join(t.TempDir(), "missing")); err == nil {
		t.Error("LoadDir(missing) returned nil error")
	}
}

func TestLangFromPath(t *testing.T) {
	t.Parallel()

	tests := []struct {
		path string
		want string
	}{
		{path: "locales/en.yaml", want: "en"},
		{path: "ja.yml", want: "ja"},
		{path: "zh-Hans.yaml", want: "zh-Hans"},
		{path: "en-US.yaml", want: "en-US"},
		{path: "pt-BR.YAML", want: "pt-BR"},
	}
	for _, tt := range tests {
		got, err := locale.LangFromPath(tt.path)
		if err != nil {
			t.Errorf("LangFromPath(%q) returned error: %v", tt.path, err)
			continue
		}
		if got != tt.want {
			t.Errorf("LangFromPath(%q) = %q, want %q", tt.path, got, tt.want)
		}
	}

	for _, path := range []string{"messages.yaml", "meta.yaml", "en_US.yaml", "EN.yaml", ".yaml", "grüße.yaml"} {
		if _, err := locale.LangFromPath(path); err == nil {
			t.Errorf("LangFromPath(%q) returned nil error", path)
		}
	}
}

func TestEntry_Params(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		src  string
		key  string
		want []template.Param
	}{
		{
			name: "no params",
			src:  "greeting: \"Hello!\"\n",
			key:  "greeting",
			want: nil,
		},
		{
			name: "single entry",
			src:  "price: \"{name} costs {price:number}\"\n",
			key:  "price",
			want: []template.Param{
				{Name: "name", Kind: template.KindString},
				{Name: "price", Kind: template.KindNumber},
			},
		},
		{
			name: "plural entry always takes count first",
			src:  "items:\n  plural:\n    one: \"One item\"\n    other: \"Many items\"\n",
			key:  "items",
			want: []template.Param{{Name: "count", Kind: template.KindInt}},
		},
		{
			name: "plural params follow canonical category order",
			src:  "items:\n  plural:\n    one: \"{count} item of {b}\"\n    other: \"{count} items of {a} and {b}\"\n",
			key:  "items",
			want: []template.Param{
				{Name: "count", Kind: template.KindInt},
				{Name: "b", Kind: template.KindString},
				{Name: "a", Kind: template.KindString},
			},
		},
		{
			name: "bare form inherits explicit kind from earlier form",
			src: "items:\n  plural:\n    one: \"{count} file, {size:number} bytes\"\n" +
				"    other: \"{count} files, {size} bytes\"\n",
			key: "items",
			want: []template.Param{
				{Name: "count", Kind: template.KindInt},
				{Name: "size", Kind: template.KindNumber},
			},
		},
		{
			name: "bare form inherits explicit kind from later form",
			src: "items:\n  plural:\n    one: \"{count} file, {size} bytes\"\n" +
				"    other: \"{count} files, {size:number} bytes\"\n",
			key: "items",
			want: []template.Param{
				{Name: "count", Kind: template.KindInt},
				{Name: "size", Kind: template.KindNumber},
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			entry := mustParseYAML(t, tt.src).Entries[tt.key]
			got, err := entry.Params()
			if err != nil {
				t.Fatalf("Params() returned error: %v", err)
			}
			if !reflect.DeepEqual(got, tt.want) {
				t.Errorf("Params() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestEntry_Params_kindConflict(t *testing.T) {
	t.Parallel()

	c := mustParseYAML(t, "items:\n  plural:\n    one: \"{n:int} item\"\n    other: \"{n:number} items\"\n")
	if _, err := c.Entries["items"].Params(); err == nil {
		t.Error("Params() returned nil error for conflicting kinds")
	}
}

func TestError_Error(t *testing.T) {
	t.Parallel()

	tests := []struct {
		err  locale.Error
		want string
	}{
		{err: locale.Error{File: "en.yaml", Line: 3, Msg: "boom"}, want: "en.yaml:3: boom"},
		{err: locale.Error{File: "en.yaml", Msg: "boom"}, want: "en.yaml: boom"},
		{err: locale.Error{Line: 3, Msg: "boom"}, want: "line 3: boom"},
		{err: locale.Error{Msg: "boom"}, want: "boom"},
	}
	for _, tt := range tests {
		if got := tt.err.Error(); got != tt.want {
			t.Errorf("%+v.Error() = %q, want %q", tt.err, got, tt.want)
		}
	}
}

func writeFile(t *testing.T, path string, content string) {
	t.Helper()
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}
}
