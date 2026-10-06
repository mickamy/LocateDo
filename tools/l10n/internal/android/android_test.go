package android_test

import (
	"bytes"
	"flag"
	"maps"
	"os"
	"os/exec"
	"path/filepath"
	"slices"
	"testing"

	"github.com/mickamy/LocateDo/tools/l10n/internal/analyze"
	"github.com/mickamy/LocateDo/tools/l10n/internal/android"
	"github.com/mickamy/LocateDo/tools/l10n/internal/locale"
	"github.com/mickamy/LocateDo/tools/l10n/internal/template"
)

var update = flag.Bool("update", false, "rewrite golden files")

func TestGenerate_golden(t *testing.T) {
	t.Parallel()

	outputs := android.Generate(loadModel(t, "basic"))
	want := []string{"values-ja/strings.xml", "values/strings.xml"}
	if got := slices.Sorted(maps.Keys(outputs)); !slices.Equal(got, want) {
		t.Fatalf("Generate() files = %v, want %v", got, want)
	}
	for _, name := range slices.Sorted(maps.Keys(outputs)) {
		t.Run(name, func(t *testing.T) {
			t.Parallel()

			got := outputs[name]
			goldenPath := filepath.Join("..", "..", "testdata", "basic", "want", "android", name)
			if *update {
				if err := os.MkdirAll(filepath.Dir(goldenPath), 0o750); err != nil {
					t.Fatal(err)
				}
				if err := os.WriteFile(goldenPath, got, 0o600); err != nil {
					t.Fatal(err)
				}
			}
			want, err := os.ReadFile(goldenPath)
			if err != nil {
				t.Fatal(err)
			}
			if !bytes.Equal(got, want) {
				t.Errorf("output differs from %s; run go test ./... -update and review the diff\n%s", goldenPath, got)
			}
		})
	}
}

func TestGenerate_isWellFormedXML(t *testing.T) {
	t.Parallel()

	if _, err := exec.LookPath("xmllint"); err != nil {
		t.Skip("xmllint is not available")
	}
	for name, data := range android.Generate(loadModel(t, "basic")) {
		t.Run(name, func(t *testing.T) {
			t.Parallel()

			path := filepath.Join(t.TempDir(), "strings.xml")
			if err := os.WriteFile(path, data, 0o600); err != nil {
				t.Fatal(err)
			}
			result, err := exec.CommandContext(t.Context(), "xmllint", "--noout", path).CombinedOutput()
			if err != nil {
				t.Fatalf("xmllint failed: %v\n%s", err, result)
			}
		})
	}
}

func TestRender(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		src  string
		want string
	}{
		{name: "plain", src: "Add a place", want: "Add a place"},
		{name: "one placeholder is still positional", src: "{distance} away", want: "%1$s away"},
		{
			name: "int and string",
			src:  "{assignee} took {count:int} items from {owner}",
			want: "%1$s took %2$d items from %3$s",
		},
		{name: "percent next to a placeholder", src: "{percent:int}% done", want: "%1$d%% done"},
		{name: "percent without placeholders stays", src: "100% done", want: "100% done"},
		{
			name: "quotes and markup",
			src:  `She said "it's 100% done" & left <3`,
			want: `She said \"it\'s 100% done\" &amp; left &lt;3`,
		},
		{name: "newline and tab", src: "Line one\nLine\ttwo", want: `Line one\nLine\ttwo`},
		{name: "backslash", src: `C:\path`, want: `C:\\path`},
		{name: "leading at sign", src: "@home", want: `\@home`},
		{name: "leading question mark", src: "?", want: `\?`},
		{name: "surrounding whitespace is quoted", src: " padded ", want: `" padded "`},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			tmpl, err := template.Parse(tt.src)
			if err != nil {
				t.Fatal(err)
			}
			msg := analyze.Message{Params: tmpl.Params()}
			if got := android.Render(msg, tmpl); got != tt.want {
				t.Errorf("Render(%q) = %q, want %q", tt.src, got, tt.want)
			}
		})
	}
}

func TestValuesDir(t *testing.T) {
	t.Parallel()

	tests := []struct {
		lang string
		want string
	}{
		{lang: "en", want: "values"},
		{lang: "ja", want: "values-ja"},
		{lang: "pt-br", want: "values-pt-rBR"},
		{lang: "zh-Hant-TW", want: "values-b+zh+Hant+TW"},
	}
	for _, tt := range tests {
		t.Run(tt.lang, func(t *testing.T) {
			t.Parallel()

			if got := android.ValuesDir(tt.lang, "en"); got != tt.want {
				t.Errorf("ValuesDir(%q) = %q, want %q", tt.lang, got, tt.want)
			}
		})
	}
}

func loadModel(t *testing.T, name string) analyze.Model {
	t.Helper()
	dir := filepath.Join("..", "..", "testdata", name)
	catalogs, err := locale.LoadDir(dir)
	if err != nil {
		t.Fatal(err)
	}
	meta, err := locale.LoadMeta(filepath.Join(dir, locale.MetaFile))
	if err != nil {
		t.Fatal(err)
	}
	model, diags := analyze.Analyze(catalogs, "en", meta)
	if analyze.HasErrors(diags) {
		t.Fatalf("Analyze() diags = %v", diags)
	}
	return model
}
