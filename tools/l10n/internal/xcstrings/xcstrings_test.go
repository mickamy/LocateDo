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

func TestGenerate_golden(t *testing.T) {
	t.Parallel()

	got := xcstrings.Generate(loadModel(t, "basic"))
	goldenPath := filepath.Join("..", "..", "testdata", "basic", "want", "Localizable.xcstrings")
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
		t.Errorf("Generate() differs from %s; run go test ./... -update and review the diff\n%s", goldenPath, got)
	}
}

func TestGenerate_compilesWithXcode(t *testing.T) {
	t.Parallel()

	if err := exec.CommandContext(t.Context(), "xcrun", "--find", "xcstringstool").Run(); err != nil {
		t.Skip("xcstringstool is not available")
	}
	dir := t.TempDir()
	path := filepath.Join(dir, "Localizable.xcstrings")
	if err := os.WriteFile(path, xcstrings.Generate(loadModel(t, "basic")), 0o600); err != nil {
		t.Fatal(err)
	}
	out, err := exec.CommandContext(t.Context(),
		"xcrun", "xcstringstool", "compile", path, "--output-directory", filepath.Join(dir, "out")).CombinedOutput()
	if err != nil {
		t.Fatalf("xcstringstool compile failed: %v\n%s", err, out)
	}
	produced := []string{
		"en.lproj/Localizable.strings",
		"en.lproj/Localizable.stringsdict",
		"ja.lproj/Localizable.strings",
		"ja.lproj/Localizable.stringsdict",
	}
	for _, name := range produced {
		if _, err := os.Stat(filepath.Join(dir, "out", name)); err != nil {
			t.Errorf("%s was not produced: %v", name, err)
		}
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
