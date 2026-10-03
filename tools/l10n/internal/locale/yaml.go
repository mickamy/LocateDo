package locale

import (
	"bytes"
	"errors"
	"fmt"
	"io"
	"iter"
	"slices"
	"strings"

	"gopkg.in/yaml.v3"

	"github.com/mickamy/LocateDo/tools/l10n/internal/template"
)

const pluralKey = "plural"

func ParseYAML(lang string, data []byte) (Catalog, error) {
	root, err := parseDocument(data)
	if err != nil {
		return Catalog{}, err
	}
	c := Catalog{Lang: lang, Entries: make(map[string]Entry)}
	if err := walkMapping(root, "", c.Entries); err != nil {
		return Catalog{}, err
	}
	return c, nil
}

func parseDocument(data []byte) (*yaml.Node, error) {
	empty := &yaml.Node{Kind: yaml.MappingNode}
	dec := yaml.NewDecoder(bytes.NewReader(data))
	var root yaml.Node
	if err := dec.Decode(&root); err != nil {
		if errors.Is(err, io.EOF) {
			return empty, nil
		}
		return nil, fmt.Errorf("parse yaml: %w", err)
	}
	var extra yaml.Node
	switch err := dec.Decode(&extra); {
	case err == nil:
		return nil, errors.New("multiple YAML documents are not supported")
	case !errors.Is(err, io.EOF):
		return nil, fmt.Errorf("parse yaml: %w", err)
	}
	if len(root.Content) == 0 {
		return empty, nil
	}
	doc := resolve(root.Content[0])
	if doc.Kind != yaml.MappingNode {
		return nil, errorAt(doc.Line, "top level must be a mapping")
	}
	return doc, nil
}

func walkMapping(n *yaml.Node, prefix string, entries map[string]Entry) error {
	seen := make(map[string]bool)
	for keyNode, valNode := range pairs(n) {
		segment, err := mappingKey(keyNode)
		if err != nil {
			return err
		}
		if seen[segment] {
			return errorAt(keyNode.Line, "duplicate key %q", segment)
		}
		seen[segment] = true
		key := segment
		if prefix != "" {
			key = prefix + "." + segment
		}
		val := resolve(valNode)
		switch val.Kind {
		case yaml.ScalarNode:
			tmpl, err := parseTemplate(key, val)
			if err != nil {
				return err
			}
			entries[key] = Entry{Key: key, Line: keyNode.Line, Single: tmpl}
		case yaml.MappingNode:
			if len(val.Content) == 0 {
				return errorAt(val.Line, "key %q: empty mapping", key)
			}
			group, ok := pluralGroup(val)
			if !ok {
				if err := walkMapping(val, key, entries); err != nil {
					return err
				}
				continue
			}
			if len(val.Content) > 2 {
				return errorAt(val.Line, "key %q: %q cannot share its mapping with other keys", key, pluralKey)
			}
			entry, err := parsePluralGroup(key, group)
			if err != nil {
				return err
			}
			entry.Line = keyNode.Line
			entries[key] = entry
		case yaml.DocumentNode, yaml.SequenceNode, yaml.AliasNode:
			return errorAt(val.Line, "key %q: value must be a string or a mapping", key)
		}
	}
	return nil
}

func pluralGroup(n *yaml.Node) (*yaml.Node, bool) {
	for keyNode, valNode := range pairs(n) {
		val := resolve(valNode)
		if keyNode.Value == pluralKey && val.Kind == yaml.MappingNode {
			return val, true
		}
	}
	return nil, false
}

func parsePluralGroup(key string, n *yaml.Node) (Entry, error) {
	forms := make(map[string]template.Template, len(n.Content)/2)
	for keyNode, valNode := range pairs(n) {
		category, err := mappingKey(keyNode)
		if err != nil {
			return Entry{}, err
		}
		if !slices.Contains(PluralCategories, category) {
			return Entry{}, errorAt(keyNode.Line, "key %q: %q is not a plural category (%s)",
				key, category, strings.Join(PluralCategories, ", "))
		}
		if _, ok := forms[category]; ok {
			return Entry{}, errorAt(keyNode.Line, "duplicate key %q", category)
		}
		formKey := key + "." + category
		val := resolve(valNode)
		if val.Kind != yaml.ScalarNode {
			return Entry{}, errorAt(val.Line, "key %q: plural form must be a string", formKey)
		}
		tmpl, err := parseTemplate(formKey, val)
		if err != nil {
			return Entry{}, err
		}
		if slices.ContainsFunc(tmpl.Params(), isNumberCount) {
			return Entry{}, errorAt(val.Line, "key %q: parameter %q must be int in plural forms", formKey, CountParam)
		}
		forms[category] = tmpl
	}
	if _, ok := forms["other"]; !ok {
		return Entry{}, errorAt(n.Line, "key %q: plural group must define %q", key, "other")
	}
	return Entry{Key: key, Plural: forms}, nil
}

func isNumberCount(p template.Param) bool {
	return p.Name == CountParam && p.Kind == template.KindNumber
}

func parseTemplate(key string, n *yaml.Node) (template.Template, error) {
	if n.Tag != "!!str" {
		return template.Template{}, errorAt(n.Line, "key %q: value must be a string", key)
	}
	tmpl, err := template.Parse(n.Value)
	if err != nil {
		return template.Template{}, errorAt(n.Line, "key %q: %v", key, err)
	}
	return tmpl, nil
}

func mappingKey(n *yaml.Node) (string, error) {
	if n.Kind != yaml.ScalarNode || n.Tag != "!!str" {
		return "", errorAt(n.Line, "key must be a string")
	}
	if !template.ValidName(n.Value) {
		return "", errorAt(n.Line, "invalid key %q: must match [a-z][a-z0-9_]*", n.Value)
	}
	return n.Value, nil
}

func resolve(n *yaml.Node) *yaml.Node {
	for n.Kind == yaml.AliasNode && n.Alias != nil {
		n = n.Alias
	}
	return n
}

func pairs(n *yaml.Node) iter.Seq2[*yaml.Node, *yaml.Node] {
	return func(yield func(*yaml.Node, *yaml.Node) bool) {
		for i := 0; i+1 < len(n.Content); i += 2 {
			if !yield(n.Content[i], n.Content[i+1]) {
				return
			}
		}
	}
}
