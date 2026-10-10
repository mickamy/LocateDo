package catalog_test

import (
	"flag"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/mickamy/LocateDo/tools/analytics/catalog/internal/catalog"
)

var update = flag.Bool("update", false, "rewrite the golden files")

func TestRenderMatchesGoldenFiles(t *testing.T) {
	t.Parallel()

	c, err := catalog.Load(filepath.Join("testdata", "catalog.yaml"))
	if err != nil {
		t.Fatal(err)
	}
	jsonData, err := c.JSON()
	if err != nil {
		t.Fatal(err)
	}
	outputs := map[string][]byte{
		"catalog.swift": c.Swift(),
		"catalog.kt":    c.Kotlin("com.example.analytics"),
		"catalog.json":  jsonData,
		"events.sql":    c.SQL(),
	}
	for name, got := range outputs {
		path := filepath.Join("testdata", name+".golden")
		if *update {
			if err := os.WriteFile(path, got, 0o600); err != nil {
				t.Fatal(err)
			}
			continue
		}
		want, err := os.ReadFile(path)
		if err != nil {
			t.Fatal(err)
		}
		if string(got) != string(want) {
			t.Errorf("%s differs from %s:\n%s", name, path, got)
		}
	}
}

func TestParseRejectsMistakes(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		yaml string
		want string
	}{
		{
			name: "unsorted",
			yaml: "events:\n  - name: todo_added\n  - name: place_added\n",
			want: `"place_added" must come after "todo_added"`,
		},
		{
			name: "duplicate",
			yaml: "screens:\n  - name: home\n  - name: home\n",
			want: `"home" must come after "home"`,
		},
		{
			name: "not snake case",
			yaml: "entries:\n  - name: homeList\n",
			want: `"homeList" is not snake_case`,
		},
		{
			name: "unknown platform",
			yaml: "screens:\n  - name: home\n    platforms: [web]\n",
			want: `unknown platform "web"`,
		},
		{
			name: "unknown type",
			yaml: "parameters:\n  - name: count\n    type: bool\n",
			want: `unknown type "bool"`,
		},
		{
			name: "string metric",
			yaml: "parameters:\n  - name: via\n    type: string\n    ga4: metric\n",
			want: "cannot be a metric",
		},
		{
			name: "unit on a dimension",
			yaml: "parameters:\n  - name: via\n    type: string\n    ga4: dimension\n    unit: SECONDS\n",
			want: "has a unit but is not a metric",
		},
		{
			name: "unknown unit",
			yaml: "parameters:\n  - name: stay\n    type: int\n    ga4: metric\n    unit: DAYS\n",
			want: `unknown unit "DAYS"`,
		},
		{
			name: "unknown field",
			yaml: "events:\n  - name: todo_added\n    params: [via]\n",
			want: "field params not found",
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			_, err := catalog.Parse([]byte(tt.yaml))
			if err == nil || !strings.Contains(err.Error(), tt.want) {
				t.Errorf("Parse() error = %v, want it to contain %q", err, tt.want)
			}
		})
	}
}
