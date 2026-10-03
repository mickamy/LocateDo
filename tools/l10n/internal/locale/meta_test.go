package locale_test

import (
	"path/filepath"
	"reflect"
	"strings"
	"testing"

	"github.com/mickamy/LocateDo/tools/l10n/internal/locale"
)

func TestParseMeta(t *testing.T) {
	t.Parallel()

	src := `
place.add.title:
  comment: "Title of the add-place screen"
todo.remaining:
  comment: "Count of open todos"
permission.always:
  comment: "Shown when asking for always-on location"
  platforms: [ios]
android_only:
  platforms:
    - android
`
	m, err := locale.ParseMeta([]byte(src))
	if err != nil {
		t.Fatalf("ParseMeta() returned error: %v", err)
	}
	if len(m) != 4 {
		t.Errorf("len(Meta) = %d, want 4", len(m))
	}

	if got := m.Comment("place.add.title"); got != "Title of the add-place screen" {
		t.Errorf("Comment(place.add.title) = %q", got)
	}
	if got := m.Comment("missing"); got != "" {
		t.Errorf("Comment(missing) = %q, want empty", got)
	}
	if got := m["permission.always"].Line; got != 6 {
		t.Errorf("permission.always Line = %d, want 6", got)
	}
	if got := m["android_only"].Platforms; !reflect.DeepEqual(got, []string{"android"}) {
		t.Errorf("android_only Platforms = %v, want [android]", got)
	}

	tests := []struct {
		key      string
		platform string
		want     bool
	}{
		{key: "place.add.title", platform: locale.PlatformIOS, want: true},
		{key: "place.add.title", platform: locale.PlatformAndroid, want: true},
		{key: "permission.always", platform: locale.PlatformIOS, want: true},
		{key: "permission.always", platform: locale.PlatformAndroid, want: false},
		{key: "android_only", platform: locale.PlatformIOS, want: false},
		{key: "android_only", platform: locale.PlatformAndroid, want: true},
		{key: "missing", platform: locale.PlatformIOS, want: true},
	}
	for _, tt := range tests {
		if got := m.Includes(tt.key, tt.platform); got != tt.want {
			t.Errorf("Includes(%q, %q) = %v, want %v", tt.key, tt.platform, got, tt.want)
		}
	}
}

func TestParseMeta_empty(t *testing.T) {
	t.Parallel()

	for _, src := range []string{"", "# nothing yet\n", "{}\n"} {
		m, err := locale.ParseMeta([]byte(src))
		if err != nil {
			t.Errorf("ParseMeta(%q) returned error: %v", src, err)
			continue
		}
		if len(m) != 0 {
			t.Errorf("ParseMeta(%q): len(Meta) = %d, want 0", src, len(m))
		}
	}
}

func TestParseMeta_error(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		src  string
	}{
		{name: "top-level sequence", src: "- a\n"},
		{name: "top-level scalar", src: "\"a\"\n"},
		{name: "non-string key", src: "123:\n  comment: \"x\"\n"},
		{name: "uppercase segment", src: "Place.title:\n  comment: \"x\"\n"},
		{name: "empty segment", src: "a..b:\n  comment: \"x\"\n"},
		{name: "leading dot", src: ".a:\n  comment: \"x\"\n"},
		{name: "scalar value", src: "a: \"x\"\n"},
		{name: "empty mapping", src: "a: {}\n"},
		{name: "unknown field", src: "a:\n  note: \"x\"\n"},
		{name: "comment not a string", src: "a:\n  comment: 1\n"},
		{name: "platforms not a list", src: "a:\n  platforms: ios\n"},
		{name: "platforms empty", src: "a:\n  platforms: []\n"},
		{name: "unknown platform", src: "a:\n  platforms: [web]\n"},
		{name: "non-string platform", src: "a:\n  platforms: [1]\n"},
		{name: "duplicate platform", src: "a:\n  platforms: [ios, ios]\n"},
		{name: "duplicate key", src: "a:\n  comment: \"x\"\na:\n  comment: \"y\"\n"},
		{name: "duplicate field", src: "a:\n  comment: \"x\"\n  comment: \"y\"\n"},
		{name: "multiple documents", src: "a:\n  comment: \"x\"\n---\nb:\n  comment: \"y\"\n"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			if _, err := locale.ParseMeta([]byte(tt.src)); err == nil {
				t.Errorf("ParseMeta(%q) returned nil error", tt.src)
			}
		})
	}
}

func TestLoadMeta(t *testing.T) {
	t.Parallel()

	dir := t.TempDir()

	m, err := locale.LoadMeta(filepath.Join(dir, locale.MetaFile))
	if err != nil {
		t.Fatalf("LoadMeta(missing) returned error: %v", err)
	}
	if len(m) != 0 {
		t.Errorf("LoadMeta(missing): len(Meta) = %d, want 0", len(m))
	}

	path := filepath.Join(dir, locale.MetaFile)
	writeFile(t, path, "greeting:\n  comment: \"Shown on launch\"\n")
	m, err = locale.LoadMeta(path)
	if err != nil {
		t.Fatalf("LoadMeta() returned error: %v", err)
	}
	if got := m.Comment("greeting"); got != "Shown on launch" {
		t.Errorf("Comment(greeting) = %q", got)
	}

	writeFile(t, path, "greeting:\n  comment: \"ok\"\nbad:\n  note: \"x\"\n")
	_, err = locale.LoadMeta(path)
	if err == nil {
		t.Fatal("LoadMeta(broken) returned nil error")
	}
	if want := path + ":4: "; !strings.HasPrefix(err.Error(), want) {
		t.Errorf("error = %q, want prefix %q", err.Error(), want)
	}
}
