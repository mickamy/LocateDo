package locale

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"slices"
	"strings"

	"github.com/mickamy/LocateDo/tools/l10n/internal/template"
)

const CountParam = "count"

var PluralCategories = []string{"zero", "one", "two", "few", "many", "other"}

var langPattern = regexp.MustCompile(`^[a-z]{2,3}(-[A-Za-z0-9]{2,8})*$`)

type Error struct {
	File string
	Line int
	Msg  string
}

func (e *Error) Error() string {
	switch {
	case e.File != "" && e.Line > 0:
		return fmt.Sprintf("%s:%d: %s", e.File, e.Line, e.Msg)
	case e.File != "":
		return e.File + ": " + e.Msg
	case e.Line > 0:
		return fmt.Sprintf("line %d: %s", e.Line, e.Msg)
	default:
		return e.Msg
	}
}

func errorAt(line int, format string, args ...any) error {
	return &Error{Line: line, Msg: fmt.Sprintf(format, args...)}
}

func withFile(err error, path string) error {
	if pe, ok := errors.AsType[*Error](err); ok {
		pe.File = path
		return pe
	}
	return fmt.Errorf("%s: %w", path, err)
}

type Entry struct {
	Key    string
	Line   int
	Single template.Template
	Plural map[string]template.Template
}

func (e Entry) Params() ([]template.Param, error) {
	if e.Plural == nil {
		return e.Single.Params(), nil
	}
	params := []template.Param{{Name: CountParam, Kind: template.KindInt}}
	index := map[string]int{CountParam: 0}
	explicit := map[string]bool{CountParam: true}
	for _, category := range PluralCategories {
		tmpl, ok := e.Plural[category]
		if !ok {
			continue
		}
		for _, p := range tmpl.Params() {
			if p.Name == CountParam {
				continue
			}
			at, seen := index[p.Name]
			if !seen {
				index[p.Name] = len(params)
				explicit[p.Name] = tmpl.Explicit(p.Name)
				params = append(params, p)
				continue
			}
			if !tmpl.Explicit(p.Name) {
				continue
			}
			if explicit[p.Name] && params[at].Kind != p.Kind {
				return nil, fmt.Errorf("key %q: parameter %q is %s in one plural form and %s in another",
					e.Key, p.Name, params[at].Kind, p.Kind)
			}
			params[at].Kind = p.Kind
			explicit[p.Name] = true
		}
	}
	return params, nil
}

type Catalog struct {
	Lang    string
	File    string
	Entries map[string]Entry
}

func ParseFile(path string) (Catalog, error) {
	if !isYAML(path) {
		return Catalog{}, fmt.Errorf("%s: unsupported locale file extension %q", path, filepath.Ext(path))
	}
	lang, err := LangFromPath(path)
	if err != nil {
		return Catalog{}, err
	}
	data, err := os.ReadFile(path)
	if err != nil {
		return Catalog{}, fmt.Errorf("read locale file: %w", err)
	}
	c, err := ParseYAML(lang, data)
	if err != nil {
		return Catalog{}, withFile(err, path)
	}
	c.File = path
	return c, nil
}

func LoadDir(dir string) ([]Catalog, error) {
	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil, fmt.Errorf("read locale directory: %w", err)
	}
	seen := make(map[string]string)
	catalogs := make([]Catalog, 0, len(entries))
	for _, e := range entries {
		name := e.Name()
		if e.IsDir() || strings.HasPrefix(name, ".") || !isYAML(name) || name == MetaFile {
			continue
		}
		c, err := ParseFile(filepath.Join(dir, name))
		if err != nil {
			return nil, err
		}
		if prev, ok := seen[c.Lang]; ok {
			return nil, fmt.Errorf("locale %s defined by both %s and %s", c.Lang, prev, name)
		}
		seen[c.Lang] = name
		catalogs = append(catalogs, c)
	}
	if len(catalogs) == 0 {
		return nil, fmt.Errorf("no locale files found in %s", dir)
	}
	slices.SortFunc(catalogs, func(a, b Catalog) int { return strings.Compare(a.Lang, b.Lang) })
	return catalogs, nil
}

func LangFromPath(path string) (string, error) {
	base := filepath.Base(path)
	stem := strings.TrimSuffix(base, filepath.Ext(base))
	if !langPattern.MatchString(stem) {
		return "", fmt.Errorf("cannot derive language from %q: want a tag like en or zh-Hans", base)
	}
	return stem, nil
}

func isYAML(path string) bool {
	switch strings.ToLower(filepath.Ext(path)) {
	case ".yaml", ".yml":
		return true
	default:
		return false
	}
}
