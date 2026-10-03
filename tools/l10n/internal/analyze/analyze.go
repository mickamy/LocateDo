package analyze

import (
	"fmt"
	"maps"
	"slices"
	"strings"

	"github.com/mickamy/LocateDo/tools/l10n/internal/locale"
	"github.com/mickamy/LocateDo/tools/l10n/internal/template"
)

type Severity int

const (
	Warning Severity = iota
	Error
)

func (s Severity) String() string {
	if s == Error {
		return "error"
	}
	return "warning"
}

type Diag struct {
	Severity Severity
	File     string
	Line     int
	Msg      string
}

func (d Diag) String() string {
	var b strings.Builder
	if d.File != "" {
		b.WriteString(d.File)
		if d.Line > 0 {
			fmt.Fprintf(&b, ":%d", d.Line)
		}
		b.WriteString(": ")
	}
	fmt.Fprintf(&b, "%s: %s", d.Severity, d.Msg)
	return b.String()
}

func HasErrors(diags []Diag) bool {
	return slices.ContainsFunc(diags, func(d Diag) bool { return d.Severity == Error })
}

type Message struct {
	Key          string
	Plural       bool
	Params       []template.Param
	Translations map[string]locale.Entry
}

func (m Message) Placeholder(name string) (int, template.Kind) {
	i := slices.IndexFunc(m.Params, func(p template.Param) bool { return p.Name == name })
	if i < 0 {
		return 0, template.KindString
	}
	return i + 1, m.Params[i].Kind
}

type Model struct {
	DefaultLang string
	Langs       []string
	Messages    []Message
	Meta        locale.Meta
}

func Analyze(catalogs []locale.Catalog, defaultLang string, meta locale.Meta) (Model, []Diag) {
	at := slices.IndexFunc(catalogs, func(c locale.Catalog) bool { return c.Lang == defaultLang })
	if at < 0 {
		return Model{}, []Diag{errorf("", 0, "default locale %s not found (available: %s)",
			defaultLang, strings.Join(langs(catalogs, ""), ", "))}
	}
	def := catalogs[at]

	messages, diags := buildMessages(def)
	if HasErrors(diags) {
		return Model{}, diags
	}
	index := make(map[string]int, len(messages))
	for i, msg := range messages {
		index[msg.Key] = i
	}
	for _, c := range catalogs {
		if c.Lang == defaultLang {
			continue
		}
		diags = append(diags, crossCheck(messages, index, c)...)
	}
	diags = append(diags, checkMeta(meta, index)...)
	diags = append(diags, checkResourceNames(messages, def)...)
	if HasErrors(diags) {
		return Model{}, diags
	}
	return Model{
		DefaultLang: defaultLang,
		Langs:       langs(catalogs, defaultLang),
		Messages:    messages,
		Meta:        meta,
	}, diags
}

func AndroidResourceName(key string) string {
	return strings.ReplaceAll(key, ".", "_")
}

func buildMessages(def locale.Catalog) ([]Message, []Diag) {
	keys := slices.Sorted(maps.Keys(def.Entries))
	messages := make([]Message, 0, len(keys))
	var diags []Diag
	for _, key := range keys {
		entry := def.Entries[key]
		params, err := entry.Params()
		if err != nil {
			diags = append(diags, errorf(def.File, entry.Line, "locale %s: %v", def.Lang, err))
			continue
		}
		diags = append(diags, checkPluralForms(def, entry)...)
		messages = append(messages, Message{
			Key:          key,
			Plural:       entry.Plural != nil,
			Params:       params,
			Translations: map[string]locale.Entry{def.Lang: entry},
		})
	}
	return messages, diags
}

func crossCheck(messages []Message, index map[string]int, c locale.Catalog) []Diag {
	var diags []Diag
	for _, key := range slices.Sorted(maps.Keys(c.Entries)) {
		entry := c.Entries[key]
		at, ok := index[key]
		if !ok {
			diags = append(diags, errorf(c.File, entry.Line,
				"locale %s: key %q does not exist in the default locale", c.Lang, key))
			continue
		}
		msg := messages[at]
		if (entry.Plural != nil) != msg.Plural {
			diags = append(diags, errorf(c.File, entry.Line,
				"locale %s: key %q: plural shape differs from the default locale", c.Lang, key))
			continue
		}
		diags = append(diags, checkPluralForms(c, entry)...)
		params, err := entry.Params()
		if err != nil {
			diags = append(diags, errorf(c.File, entry.Line, "locale %s: %v", c.Lang, err))
			continue
		}
		paramDiags := checkParams(msg, c, entry, params)
		diags = append(diags, paramDiags...)
		if len(paramDiags) == 0 {
			msg.Translations[c.Lang] = entry
		}
	}
	for _, msg := range messages {
		if _, ok := c.Entries[msg.Key]; !ok {
			diags = append(diags, warningf(c.File, 0, "locale %s: missing key %q", c.Lang, msg.Key))
		}
	}
	return diags
}

func checkParams(msg Message, c locale.Catalog, entry locale.Entry, params []template.Param) []Diag {
	var diags []Diag
	for _, p := range params {
		i := slices.IndexFunc(msg.Params, func(dp template.Param) bool { return dp.Name == p.Name })
		if i < 0 {
			diags = append(diags, errorf(c.File, entry.Line,
				"locale %s: key %q: parameter %q does not exist in the default locale", c.Lang, entry.Key, p.Name))
			continue
		}
		if p.Kind != template.KindString && p.Kind != msg.Params[i].Kind {
			diags = append(diags, errorf(c.File, entry.Line,
				"locale %s: key %q: parameter %q is %s, but the default locale has %s",
				c.Lang, entry.Key, p.Name, p.Kind, msg.Params[i].Kind))
		}
	}
	return diags
}

func checkPluralForms(c locale.Catalog, entry locale.Entry) []Diag {
	if entry.Plural == nil {
		return nil
	}
	required, known := requiredCategories(c.Lang)
	if !known {
		return []Diag{warningf(c.File, entry.Line,
			"locale %s: plural rules for this language are unknown; add them to analyze/plural.go", c.Lang)}
	}
	var missing []string
	for _, category := range required {
		if _, ok := entry.Plural[category]; !ok {
			missing = append(missing, category)
		}
	}
	if len(missing) == 0 {
		return nil
	}
	return []Diag{warningf(c.File, entry.Line,
		"locale %s: key %q: missing plural forms %s", c.Lang, entry.Key, strings.Join(missing, ", "))}
}

func checkMeta(meta locale.Meta, index map[string]int) []Diag {
	var diags []Diag
	for _, key := range slices.Sorted(maps.Keys(meta.Entries)) {
		if _, ok := index[key]; !ok {
			diags = append(diags, errorf(meta.File, meta.Entries[key].Line,
				"meta: key %q does not exist in the default locale", key))
		}
	}
	return diags
}

func checkResourceNames(messages []Message, def locale.Catalog) []Diag {
	seen := make(map[string]string, len(messages))
	var diags []Diag
	for _, msg := range messages {
		name := AndroidResourceName(msg.Key)
		if prev, ok := seen[name]; ok {
			diags = append(diags, errorf(def.File, def.Entries[msg.Key].Line,
				"keys %q and %q both map to Android resource name %q", prev, msg.Key, name))
			continue
		}
		seen[name] = msg.Key
	}
	return diags
}

func langs(catalogs []locale.Catalog, first string) []string {
	out := make([]string, 0, len(catalogs))
	for _, c := range catalogs {
		if c.Lang != first {
			out = append(out, c.Lang)
		}
	}
	slices.Sort(out)
	if first != "" {
		out = slices.Insert(out, 0, first)
	}
	return out
}

func errorf(file string, line int, format string, args ...any) Diag {
	return Diag{Severity: Error, File: file, Line: line, Msg: fmt.Sprintf(format, args...)}
}

func warningf(file string, line int, format string, args ...any) Diag {
	return Diag{Severity: Warning, File: file, Line: line, Msg: fmt.Sprintf(format, args...)}
}
