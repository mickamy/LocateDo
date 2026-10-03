package xcstrings

import (
	"bytes"
	"fmt"
	"maps"
	"slices"
	"strings"

	"github.com/mickamy/LocateDo/tools/l10n/internal/analyze"
	"github.com/mickamy/LocateDo/tools/l10n/internal/locale"
	"github.com/mickamy/LocateDo/tools/l10n/internal/template"
)

type entry struct {
	key string
	val any
}

type object []entry

func Generate(m analyze.Model) []byte {
	strs := make(object, 0, len(m.Messages))
	for _, msg := range m.Messages {
		if !m.Meta.Includes(msg.Key, locale.PlatformIOS) {
			continue
		}
		strs = append(strs, entry{msg.Key, message(m, msg)})
	}
	root := object{
		{"sourceLanguage", m.DefaultLang},
		{"strings", strs},
		{"version", "1.0"},
	}
	var b bytes.Buffer
	writeObject(&b, root, 0)
	b.WriteByte('\n')
	return b.Bytes()
}

func message(m analyze.Model, msg analyze.Message) object {
	var o object
	if comment := m.Meta.Comment(msg.Key); comment != "" {
		o = append(o, entry{"comment", comment})
	}
	o = append(o, entry{"extractionState", "manual"})
	locs := make(object, 0, len(msg.Translations))
	for _, lang := range slices.Sorted(maps.Keys(msg.Translations)) {
		locs = append(locs, entry{lang, localization(msg, msg.Translations[lang])})
	}
	return append(o, entry{"localizations", locs})
}

func localization(msg analyze.Message, e locale.Entry) object {
	if !msg.Plural {
		return object{{"stringUnit", stringUnit(render(msg, e.Single))}}
	}
	forms := make(object, 0, len(e.Plural))
	for _, category := range slices.Sorted(maps.Keys(e.Plural)) {
		forms = append(forms, entry{category, object{{"stringUnit", stringUnit(render(msg, e.Plural[category]))}}})
	}
	return object{{"variations", object{{"plural", forms}}}}
}

func stringUnit(value string) object {
	return object{{"state", "translated"}, {"value", value}}
}

func render(msg analyze.Message, tmpl template.Template) string {
	formatted := len(msg.Params) > 0
	positional := len(msg.Params) > 1
	var b strings.Builder
	for _, seg := range tmpl.Segments() {
		if seg.Param == "" {
			if formatted {
				b.WriteString(strings.ReplaceAll(seg.Text, "%", "%%"))
			} else {
				b.WriteString(seg.Text)
			}
			continue
		}
		pos, kind := msg.Placeholder(seg.Param)
		b.WriteByte('%')
		if positional {
			fmt.Fprintf(&b, "%d$", pos)
		}
		b.WriteString(specifier(kind))
	}
	return b.String()
}

func specifier(kind template.Kind) string {
	if kind == template.KindInt {
		return "lld"
	}
	return "@"
}

func writeObject(b *bytes.Buffer, o object, depth int) {
	indent := strings.Repeat("  ", depth)
	if len(o) == 0 {
		b.WriteString("{\n\n" + indent + "}")
		return
	}
	b.WriteString("{\n")
	for i, e := range o {
		b.WriteString(indent + "  " + quote(e.key) + " : ")
		switch v := e.val.(type) {
		case string:
			b.WriteString(quote(v))
		case object:
			writeObject(b, v, depth+1)
		default:
			panic(fmt.Sprintf("xcstrings: unsupported value %T", v))
		}
		if i < len(o)-1 {
			b.WriteByte(',')
		}
		b.WriteByte('\n')
	}
	b.WriteString(indent + "}")
}

// quote escapes the way Xcode's JSON writer does, including the forward slash.
func quote(s string) string {
	var b strings.Builder
	b.WriteByte('"')
	for _, r := range s {
		switch r {
		case '"':
			b.WriteString(`\"`)
		case '\\':
			b.WriteString(`\\`)
		case '/':
			b.WriteString(`\/`)
		case '\n':
			b.WriteString(`\n`)
		case '\r':
			b.WriteString(`\r`)
		case '\t':
			b.WriteString(`\t`)
		default:
			if r < 0x20 {
				fmt.Fprintf(&b, `\u%04x`, r)
			} else {
				b.WriteRune(r)
			}
		}
	}
	b.WriteByte('"')
	return b.String()
}
