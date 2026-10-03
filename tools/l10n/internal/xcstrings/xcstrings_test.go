package xcstrings_test

import (
	"bytes"
	"flag"
	"os"
	"os/exec"
	"path/filepath"
	"testing"

	"github.com/mickamy/LocateDo/tools/l10n/internal/analyze"
	"github.com/mickamy/LocateDo/tools/l10n/internal/locale"
	"github.com/mickamy/LocateDo/tools/l10n/internal/xcstrings"
)

var update = flag.Bool("update", false, "rewrite golden files")

var outputs = []struct {
	name     string
	generate func(analyze.Model) []byte
}{
	{name: "Localizable.xcstrings", generate: xcstrings.Generate},
	{name: "InfoPlist.xcstrings", generate: xcstrings.GenerateInfoPlist},
}

func TestGenerate_golden(t *testing.T) {
	t.Parallel()

	model := loadModel(t, "basic")
	for _, out := range outputs {
		t.Run(out.name, func(t *testing.T) {
			t.Parallel()

			got := out.generate(model)
			goldenPath := filepath.Join("..", "..", "testdata", "basic", "want", out.name)
			if *update {
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

func TestGenerate_compilesWithXcode(t *testing.T) {
	t.Parallel()

	if err := exec.CommandContext(t.Context(), "xcrun", "--find", "xcstringstool").Run(); err != nil {
		t.Skip("xcstringstool is not available")
	}
	model := loadModel(t, "basic")
	for _, out := range outputs {
		t.Run(out.name, func(t *testing.T) {
			t.Parallel()

			dir := t.TempDir()
			path := filepath.Join(dir, out.name)
			if err := os.WriteFile(path, out.generate(model), 0o600); err != nil {
				t.Fatal(err)
			}
			result, err := exec.CommandContext(t.Context(),
				"xcrun", "xcstringstool", "compile", path, "--output-directory", filepath.Join(dir, "out")).CombinedOutput()
			if err != nil {
				t.Fatalf("xcstringstool compile failed: %v\n%s", err, result)
			}
			for _, lang := range []string{"en", "ja"} {
				if _, err := os.Stat(filepath.Join(dir, "out", lang+".lproj")); err != nil {
					t.Errorf("%s.lproj was not produced: %v", lang, err)
				}
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
