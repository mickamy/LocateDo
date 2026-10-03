package template

import (
	"fmt"
	"slices"
	"strings"
)

type Kind int

const (
	KindString Kind = iota // {name} or {name:string}
	KindInt                // {name:int}
	KindNumber             // {name:number}
)

func (k Kind) String() string {
	switch k {
	case KindString:
		return "string"
	case KindInt:
		return "int"
	case KindNumber:
		return "number"
	default:
		return fmt.Sprintf("Kind(%d)", int(k))
	}
}

type Param struct {
	Name string
	Kind Kind
}

type Segment struct {
	Text  string
	Param string
	Kind  Kind
}

type Template struct {
	segments []segment
	params   []Param
	explicit map[string]bool
}

type segment struct {
	literal string
	param   int
}

func Parse(src string) (Template, error) {
	var t Template
	index := make(map[string]int)
	explicit := make(map[string]bool)
	var literal strings.Builder
	flush := func() {
		if literal.Len() == 0 {
			return
		}
		t.segments = append(t.segments, segment{literal: literal.String(), param: -1})
		literal.Reset()
	}
	for i := 0; i < len(src); {
		switch {
		case strings.HasPrefix(src[i:], "{{"):
			literal.WriteByte('{')
			i += 2
		case strings.HasPrefix(src[i:], "}}"):
			literal.WriteByte('}')
			i += 2
		case src[i] == '}':
			return Template{}, fmt.Errorf("unmatched %q at index %d", "}", i)
		case src[i] == '{':
			end := strings.IndexByte(src[i:], '}')
			if end < 0 {
				return Template{}, fmt.Errorf("unclosed placeholder at index %d", i)
			}
			p, hasKind, err := parsePlaceholder(src[i+1 : i+end])
			if err != nil {
				return Template{}, fmt.Errorf("placeholder at index %d: %w", i, err)
			}
			idx, seen := index[p.Name]
			if !seen {
				idx = len(t.params)
				index[p.Name] = idx
				t.params = append(t.params, p)
			}
			if hasKind {
				if explicit[p.Name] && t.params[idx].Kind != p.Kind {
					return Template{}, fmt.Errorf("parameter %q declared as both %s and %s",
						p.Name, t.params[idx].Kind, p.Kind)
				}
				t.params[idx].Kind = p.Kind
				explicit[p.Name] = true
			}
			flush()
			t.segments = append(t.segments, segment{param: idx})
			i += end + 1
		default:
			literal.WriteByte(src[i])
			i++
		}
	}
	flush()
	t.explicit = explicit
	return t, nil
}

func (t Template) Params() []Param {
	return slices.Clone(t.params)
}

func (t Template) Explicit(name string) bool {
	return t.explicit[name]
}

func (t Template) Segments() []Segment {
	segs := make([]Segment, 0, len(t.segments))
	for _, seg := range t.segments {
		if seg.param < 0 {
			segs = append(segs, Segment{Text: seg.literal})
			continue
		}
		p := t.params[seg.param]
		segs = append(segs, Segment{Param: p.Name, Kind: p.Kind})
	}
	return segs
}

func ValidName(s string) bool {
	if s == "" {
		return false
	}
	for i, c := range s {
		switch {
		case 'a' <= c && c <= 'z':
		case c == '_' || ('0' <= c && c <= '9'):
			if i == 0 {
				return false
			}
		default:
			return false
		}
	}
	return true
}

func parsePlaceholder(body string) (Param, bool, error) {
	name, kindName, hasKind := strings.Cut(body, ":")
	p := Param{Name: name}
	if hasKind {
		kind, ok := parseKind(kindName)
		if !ok {
			return Param{}, false, fmt.Errorf("unknown kind %q", kindName)
		}
		p.Kind = kind
	}
	if !ValidName(name) {
		return Param{}, false, fmt.Errorf("invalid name %q", name)
	}
	return p, hasKind, nil
}

func parseKind(s string) (Kind, bool) {
	switch s {
	case "string":
		return KindString, true
	case "int":
		return KindInt, true
	case "number":
		return KindNumber, true
	default:
		return 0, false
	}
}
