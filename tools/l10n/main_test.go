package main_test

import (
	"bytes"
	"os"
	"path/filepath"
	"strings"
	"testing"

	l10n "github.com/mickamy/LocateDo/tools/l10n"
)

const enSrc = `
app:
  name: "LocateDo"
todo:
  remaining:
    plural:
      one: "{count:int} item left"
      other: "{count:int} items left"
`

const jaSrc = `
app:
  name: "LocateDo"
todo:
  remaining:
    plural:
      other: "残り {count} 件"
`

func TestRun_generateThenCheck(t *testing.T) {
	t.Parallel()

	src := writeFixture(t, enSrc, jaSrc)
	out := filepath.Join(t.TempDir(), "ios", "Localizable.xcstrings")
	var stdout, stderr bytes.Buffer

	if err := l10n.Run([]string{"generate", "-src", src, "-xcstrings", out}, &stdout, &stderr); err != nil {
		t.Fatalf("generate returned error: %v\nstderr: %s", err, stderr.String())
	}
	if !strings.Contains(stdout.String(), "wrote "+out) {
		t.Errorf("stdout = %q, want it to report the written file", stdout.String())
	}
	if stderr.Len() != 0 {
		t.Errorf("stderr = %q, want empty", stderr.String())
	}
	data, err := os.ReadFile(out)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(data), `"残り %lld 件"`) {
		t.Errorf("generated file does not contain the ja plural form:\n%s", data)
	}

	if err := l10n.Run([]string{"check", "-src", src, "-xcstrings", out}, &stdout, &stderr); err != nil {
		t.Errorf("check after generate returned error: %v", err)
	}
}

func TestRun_checkStale(t *testing.T) {
	t.Parallel()

	src := writeFixture(t, enSrc, jaSrc)
	out := filepath.Join(t.TempDir(), "Localizable.xcstrings")
	var discard bytes.Buffer

	err := l10n.Run([]string{"check", "-src", src, "-xcstrings", out}, &discard, &discard)
	if err == nil || !strings.Contains(err.Error(), "out of date") {
		t.Errorf("check with a missing file: err = %v, want out of date", err)
	}

	if err := l10n.Run([]string{"generate", "-src", src, "-xcstrings", out}, &discard, &discard); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(out, []byte("{}\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	err = l10n.Run([]string{"check", "-src", src, "-xcstrings", out}, &discard, &discard)
	if err == nil || !strings.Contains(err.Error(), "out of date") {
		t.Errorf("check with a stale file: err = %v, want out of date", err)
	}
}

func TestRun_warningsFailCheckButNotGenerate(t *testing.T) {
	t.Parallel()

	src := writeFixture(t, enSrc, "app:\n  name: \"LocateDo\"\n")
	out := filepath.Join(t.TempDir(), "Localizable.xcstrings")
	var stdout, stderr bytes.Buffer

	if err := l10n.Run([]string{"generate", "-src", src, "-xcstrings", out}, &stdout, &stderr); err != nil {
		t.Fatalf("generate returned error: %v", err)
	}
	if !strings.Contains(stderr.String(), `warning: locale ja: missing key "todo.remaining"`) {
		t.Errorf("stderr = %q, want the untranslated-key warning", stderr.String())
	}
	if _, err := os.Stat(out); err != nil {
		t.Errorf("generate did not write the file: %v", err)
	}

	err := l10n.Run([]string{"check", "-src", src, "-xcstrings", out}, &stdout, &stderr)
	if err == nil || !strings.Contains(err.Error(), "fix the diagnostics") {
		t.Errorf("check with warnings: err = %v, want a diagnostics failure", err)
	}
}

func TestRun_errorsStopGenerate(t *testing.T) {
	t.Parallel()

	src := writeFixture(t, enSrc, jaSrc+"extra: \"not in en\"\n")
	out := filepath.Join(t.TempDir(), "Localizable.xcstrings")
	var stdout, stderr bytes.Buffer

	err := l10n.Run([]string{"generate", "-src", src, "-xcstrings", out}, &stdout, &stderr)
	if err == nil || !strings.Contains(err.Error(), "fix the errors") {
		t.Errorf("generate with errors: err = %v, want an errors failure", err)
	}
	if !strings.Contains(stderr.String(), "error: locale ja: key \"extra\" does not exist") {
		t.Errorf("stderr = %q, want the unknown-key error", stderr.String())
	}
	if _, err := os.Stat(out); err == nil {
		t.Error("generate wrote a file despite errors")
	}
}

func TestRun_infoplist(t *testing.T) {
	t.Parallel()

	src := writeFixture(t, enSrc+"permission:\n  when_in_use: \"Shows places near you.\"\n",
		jaSrc+"permission:\n  when_in_use: \"近くの場所を表示します。\"\n")
	meta := "permission.when_in_use:\n  infoplist: NSLocationWhenInUseUsageDescription\n"
	if err := os.WriteFile(filepath.Join(src, "meta.yaml"), []byte(meta), 0o600); err != nil {
		t.Fatal(err)
	}
	outDir := t.TempDir()
	localizable := filepath.Join(outDir, "Localizable.xcstrings")
	infoplist := filepath.Join(outDir, "InfoPlist.xcstrings")
	var discard bytes.Buffer

	err := l10n.Run([]string{"generate", "-src", src, "-xcstrings", localizable}, &discard, &discard)
	if err == nil || !strings.Contains(err.Error(), "-infoplist is required") {
		t.Errorf("generate without -infoplist: err = %v, want a required-flag error", err)
	}

	args := []string{"generate", "-src", src, "-xcstrings", localizable, "-infoplist", infoplist}
	if err := l10n.Run(args, &discard, &discard); err != nil {
		t.Fatalf("generate returned error: %v", err)
	}
	data, err := os.ReadFile(infoplist)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(data), `"NSLocationWhenInUseUsageDescription" : {`) {
		t.Errorf("InfoPlist.xcstrings does not contain the plist key:\n%s", data)
	}
	data, err = os.ReadFile(localizable)
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(string(data), "permission.when_in_use") {
		t.Errorf("Localizable.xcstrings still contains the infoplist key:\n%s", data)
	}

	args[0] = "check"
	if err := l10n.Run(args, &discard, &discard); err != nil {
		t.Errorf("check after generate returned error: %v", err)
	}
}

func TestRun_android(t *testing.T) {
	t.Parallel()

	src := writeFixture(t, enSrc, jaSrc)
	outDir := t.TempDir()
	xcstrings := filepath.Join(outDir, "Localizable.xcstrings")
	res := filepath.Join(outDir, "res")
	var discard bytes.Buffer

	args := []string{"generate", "-src", src, "-xcstrings", xcstrings, "-android", res}
	if err := l10n.Run(args, &discard, &discard); err != nil {
		t.Fatalf("generate returned error: %v", err)
	}
	data, err := os.ReadFile(filepath.Join(res, "values-ja", "strings.xml"))
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(data), `<item quantity="other">残り %1$d 件</item>`) {
		t.Errorf("values-ja/strings.xml does not contain the ja plural form:\n%s", data)
	}
	data, err = os.ReadFile(filepath.Join(res, "values", "strings.xml"))
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(data), `<string name="app_name">LocateDo</string>`) {
		t.Errorf("values/strings.xml does not contain app_name:\n%s", data)
	}

	args[0] = "check"
	if err := l10n.Run(args, &discard, &discard); err != nil {
		t.Errorf("check after generate returned error: %v", err)
	}
}

func TestRun_usage(t *testing.T) {
	t.Parallel()

	src := writeFixture(t, enSrc, jaSrc)
	tests := []struct {
		name string
		args []string
		want string
	}{
		{name: "no args", args: nil, want: "usage:"},
		{name: "unknown command", args: []string{"build"}, want: `unknown command "build"`},
		{name: "missing flags", args: []string{"generate", "-src", src}, want: "-src and -xcstrings are required"},
		{name: "bad flag", args: []string{"check", "-nope"}, want: "parse flags"},
		{name: "missing source dir", args: []string{"check", "-src", filepath.Join(src, "nope"), "-xcstrings", "x"},
			want: "read locale directory"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			var discard bytes.Buffer
			err := l10n.Run(tt.args, &discard, &discard)
			if err == nil || !strings.Contains(err.Error(), tt.want) {
				t.Errorf("Run(%v) err = %v, want it to contain %q", tt.args, err, tt.want)
			}
		})
	}
}

func writeFixture(t *testing.T, en, ja string) string {
	t.Helper()
	dir := t.TempDir()
	for name, content := range map[string]string{"en.yaml": en, "ja.yaml": ja} {
		if err := os.WriteFile(filepath.Join(dir, name), []byte(content), 0o600); err != nil {
			t.Fatal(err)
		}
	}
	return dir
}
