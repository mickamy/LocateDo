package locale

import (
	"errors"
	"fmt"
	"io/fs"
	"os"
	"regexp"
	"slices"
	"strings"

	"gopkg.in/yaml.v3"

	"github.com/mickamy/LocateDo/tools/l10n/internal/template"
)

const (
	MetaFile        = "meta.yaml"
	PlatformIOS     = "ios"
	PlatformAndroid = "android"
)

var Platforms = []string{PlatformIOS, PlatformAndroid}

var plistKeyPattern = regexp.MustCompile(`^[A-Za-z][A-Za-z0-9]*$`)

type MetaEntry struct {
	Line      int
	Comment   string
	Platforms []string
	Infoplist string
}

type Meta struct {
	File    string
	Entries map[string]MetaEntry
}

func (m Meta) Comment(key string) string {
	return m.Entries[key].Comment
}

func (m Meta) Infoplist(key string) string {
	return m.Entries[key].Infoplist
}

func (m Meta) Includes(key, platform string) bool {
	e, ok := m.Entries[key]
	if !ok {
		return true
	}
	if e.Infoplist != "" && platform != PlatformIOS {
		return false
	}
	if len(e.Platforms) == 0 {
		return true
	}
	return slices.Contains(e.Platforms, platform)
}

func LoadMeta(path string) (Meta, error) {
	data, err := os.ReadFile(path)
	if errors.Is(err, fs.ErrNotExist) {
		return Meta{File: path}, nil
	}
	if err != nil {
		return Meta{}, fmt.Errorf("read meta file: %w", err)
	}
	m, err := ParseMeta(data)
	if err != nil {
		return Meta{}, withFile(err, path)
	}
	m.File = path
	return m, nil
}

func ParseMeta(data []byte) (Meta, error) {
	root, err := parseDocument(data)
	if err != nil {
		return Meta{}, err
	}
	m := Meta{Entries: make(map[string]MetaEntry, len(root.Content)/2)}
	for keyNode, valNode := range pairs(root) {
		key, err := metaKey(keyNode)
		if err != nil {
			return Meta{}, err
		}
		if _, ok := m.Entries[key]; ok {
			return Meta{}, errorAt(keyNode.Line, "duplicate key %q", key)
		}
		entry, err := parseMetaEntry(key, resolve(valNode))
		if err != nil {
			return Meta{}, err
		}
		entry.Line = keyNode.Line
		m.Entries[key] = entry
	}
	return m, nil
}

func metaKey(n *yaml.Node) (string, error) {
	if n.Kind != yaml.ScalarNode || n.Tag != "!!str" {
		return "", errorAt(n.Line, "key must be a string")
	}
	for segment := range strings.SplitSeq(n.Value, ".") {
		if !template.ValidName(segment) {
			return "", errorAt(n.Line, "invalid key %q: each dot-separated segment must match [a-z][a-z0-9_]*", n.Value)
		}
	}
	return n.Value, nil
}

func parseMetaEntry(key string, n *yaml.Node) (MetaEntry, error) {
	if n.Kind != yaml.MappingNode || len(n.Content) == 0 {
		return MetaEntry{}, errorAt(n.Line, "key %q: value must be a mapping with comment, platforms, or infoplist", key)
	}
	var entry MetaEntry
	seen := make(map[string]bool)
	for fieldNode, valNode := range pairs(n) {
		field := fieldNode.Value
		if seen[field] {
			return MetaEntry{}, errorAt(fieldNode.Line, "key %q: duplicate field %q", key, field)
		}
		seen[field] = true
		val := resolve(valNode)
		switch field {
		case "comment":
			if val.Kind != yaml.ScalarNode || val.Tag != "!!str" {
				return MetaEntry{}, errorAt(val.Line, "key %q: comment must be a string", key)
			}
			entry.Comment = val.Value
		case "platforms":
			platforms, err := parsePlatforms(key, val)
			if err != nil {
				return MetaEntry{}, err
			}
			entry.Platforms = platforms
		case "infoplist":
			if val.Kind != yaml.ScalarNode || val.Tag != "!!str" || !plistKeyPattern.MatchString(val.Value) {
				return MetaEntry{}, errorAt(val.Line,
					"key %q: infoplist must be an Info.plist key such as NSLocationWhenInUseUsageDescription", key)
			}
			entry.Infoplist = val.Value
		default:
			return MetaEntry{}, errorAt(fieldNode.Line,
				"key %q: unknown field %q (want comment, platforms, or infoplist)", key, field)
		}
	}
	if entry.Infoplist != "" && slices.Contains(entry.Platforms, PlatformAndroid) {
		return MetaEntry{}, errorAt(n.Line, "key %q: infoplist strings are iOS-only; drop android from platforms", key)
	}
	return entry, nil
}

func parsePlatforms(key string, n *yaml.Node) ([]string, error) {
	if n.Kind != yaml.SequenceNode || len(n.Content) == 0 {
		return nil, errorAt(n.Line, "key %q: platforms must be a non-empty list", key)
	}
	platforms := make([]string, 0, len(n.Content))
	for _, item := range n.Content {
		p := resolve(item)
		if p.Kind != yaml.ScalarNode || p.Tag != "!!str" || !slices.Contains(Platforms, p.Value) {
			return nil, errorAt(p.Line, "key %q: unknown platform %q (want %s)",
				key, p.Value, strings.Join(Platforms, " or "))
		}
		if slices.Contains(platforms, p.Value) {
			return nil, errorAt(p.Line, "key %q: duplicate platform %q", key, p.Value)
		}
		platforms = append(platforms, p.Value)
	}
	return platforms, nil
}
