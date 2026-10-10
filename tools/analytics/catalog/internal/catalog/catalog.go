package catalog

import (
	"bytes"
	"errors"
	"fmt"
	"os"
	"regexp"
	"slices"

	"gopkg.in/yaml.v3"
)

const (
	PlatformIOS     = "ios"
	PlatformAndroid = "android"
)

type Name struct {
	Name      string   `yaml:"name"`
	Doc       string   `yaml:"doc"`
	Platforms []string `yaml:"platforms"`
}

type Parameter struct {
	Name      string   `yaml:"name"`
	Type      string   `yaml:"type"`
	GA4       string   `yaml:"ga4"`
	Unit      string   `yaml:"unit"`
	Column    string   `yaml:"column"`
	SentBy    string   `yaml:"sent_by"`
	Platforms []string `yaml:"platforms"`
}

type Catalog struct {
	Events         []Name      `yaml:"events"`
	Parameters     []Parameter `yaml:"parameters"`
	UserProperties []Name      `yaml:"user_properties"`
	Screens        []Name      `yaml:"screens"`
	Entries        []Name      `yaml:"entries"`
}

var snakeCase = regexp.MustCompile(`^[a-z][a-z0-9]*(_[a-z0-9]+)*$`)

var units = []string{"STANDARD", "SECONDS", "MINUTES", "HOURS", "METERS"}

func Load(path string) (Catalog, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return Catalog{}, fmt.Errorf("read catalog: %w", err)
	}
	return Parse(data)
}

func Parse(data []byte) (Catalog, error) {
	decoder := yaml.NewDecoder(bytes.NewReader(data))
	decoder.KnownFields(true)
	var c Catalog
	if err := decoder.Decode(&c); err != nil {
		return Catalog{}, fmt.Errorf("parse catalog: %w", err)
	}
	if err := c.validate(); err != nil {
		return Catalog{}, err
	}
	return c, nil
}

func (c Catalog) validate() error {
	errs := validateNames("events", c.Events)
	errs = append(errs, validateNames("user_properties", c.UserProperties)...)
	errs = append(errs, validateNames("screens", c.Screens)...)
	errs = append(errs, validateNames("entries", c.Entries)...)
	names := make([]Name, 0, len(c.Parameters))
	for _, p := range c.Parameters {
		names = append(names, Name{Name: p.Name, Platforms: p.Platforms})
		errs = append(errs, validateParameter(p)...)
	}
	errs = append(errs, validateNames("parameters", names)...)
	return errors.Join(errs...)
}

func validateNames(section string, names []Name) []error {
	var errs []error
	for i, n := range names {
		if !snakeCase.MatchString(n.Name) {
			errs = append(errs, fmt.Errorf("%s: %q is not snake_case", section, n.Name))
		}
		if i > 0 && names[i-1].Name >= n.Name {
			errs = append(errs, fmt.Errorf("%s: %q must come after %q (sorted, no duplicates)", section, n.Name,
				names[i-1].Name))
		}
		for _, platform := range n.Platforms {
			if platform != PlatformIOS && platform != PlatformAndroid {
				errs = append(errs, fmt.Errorf("%s: %q has unknown platform %q", section, n.Name, platform))
			}
		}
	}
	return errs
}

func validateParameter(p Parameter) []error {
	var errs []error
	if !slices.Contains([]string{"string", "int", "number"}, p.Type) {
		errs = append(errs, fmt.Errorf("parameters: %q has unknown type %q", p.Name, p.Type))
	}
	switch p.GA4 {
	case "", "dimension":
		if p.Unit != "" {
			errs = append(errs, fmt.Errorf("parameters: %q has a unit but is not a metric", p.Name))
		}
	case "metric":
		if p.Unit != "" && !slices.Contains(units, p.Unit) {
			errs = append(errs, fmt.Errorf("parameters: %q has unknown unit %q", p.Name, p.Unit))
		}
		if p.Type == "string" {
			errs = append(errs, fmt.Errorf("parameters: %q is a string and cannot be a metric", p.Name))
		}
	default:
		errs = append(errs, fmt.Errorf("parameters: %q has unknown ga4 %q", p.Name, p.GA4))
	}
	if p.SentBy != "" && p.SentBy != "revenuecat" {
		errs = append(errs, fmt.Errorf("parameters: %q has unknown sent_by %q", p.Name, p.SentBy))
	}
	if p.Column != "" && !snakeCase.MatchString(p.Column) {
		errs = append(errs, fmt.Errorf("parameters: %q has column %q that is not snake_case", p.Name, p.Column))
	}
	return errs
}

func (n Name) on(platform string) bool {
	return len(n.Platforms) == 0 || slices.Contains(n.Platforms, platform)
}

func (p Parameter) on(platform string) bool {
	if p.SentBy != "" {
		return false
	}
	return Name{Platforms: p.Platforms}.on(platform)
}

func (p Parameter) column() string {
	if p.Column != "" {
		return p.Column
	}
	return p.Name
}

func (p Parameter) unit() string {
	if p.Unit == "" {
		return "STANDARD"
	}
	return p.Unit
}

func namesOn(names []Name, platform string) []string {
	var out []string
	for _, n := range names {
		if n.on(platform) {
			out = append(out, n.Name)
		}
	}
	return out
}

func parametersOn(parameters []Parameter, platform string) []string {
	var out []string
	for _, p := range parameters {
		if p.on(platform) {
			out = append(out, p.Name)
		}
	}
	return out
}
