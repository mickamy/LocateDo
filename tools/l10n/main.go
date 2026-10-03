package main

import (
	"bytes"
	"errors"
	"flag"
	"fmt"
	"io"
	"io/fs"
	"maps"
	"os"
	"path/filepath"
	"slices"
	"strings"

	"github.com/mickamy/LocateDo/tools/l10n/internal/analyze"
	"github.com/mickamy/LocateDo/tools/l10n/internal/locale"
	"github.com/mickamy/LocateDo/tools/l10n/internal/xcstrings"
)

const usage = "usage: l10n <generate|check> -src <dir> -xcstrings <path> [-default <lang>]"

type options struct {
	src         string
	defaultLang string
	xcstrings   string
}

func main() {
	if err := run(os.Args[1:], os.Stdout, os.Stderr); err != nil {
		fmt.Fprintln(os.Stderr, "l10n:", err)
		os.Exit(1)
	}
}

func run(args []string, stdout, stderr io.Writer) error {
	if len(args) == 0 {
		return errors.New(usage)
	}
	cmd := args[0]
	if cmd != "generate" && cmd != "check" {
		return fmt.Errorf("unknown command %q\n%s", cmd, usage)
	}
	opts, err := parseFlags(cmd, args[1:], stderr)
	if err != nil {
		return err
	}
	outputs, diags, err := build(opts)
	if err != nil {
		return err
	}
	for _, d := range diags {
		fmt.Fprintln(stderr, d)
	}
	if cmd == "check" {
		if len(diags) > 0 {
			return errors.New("fix the diagnostics above")
		}
		return compare(outputs)
	}
	if analyze.HasErrors(diags) {
		return errors.New("fix the errors above")
	}
	return write(outputs, stdout)
}

func parseFlags(cmd string, args []string, stderr io.Writer) (options, error) {
	fs := flag.NewFlagSet("l10n "+cmd, flag.ContinueOnError)
	fs.SetOutput(stderr)
	var opts options
	fs.StringVar(&opts.src, "src", "", "directory holding <lang>.yaml files and meta.yaml")
	fs.StringVar(&opts.defaultLang, "default", "en", "default language")
	fs.StringVar(&opts.xcstrings, "xcstrings", "", "Localizable.xcstrings to generate")
	if err := fs.Parse(args); err != nil {
		return options{}, fmt.Errorf("parse flags: %w", err)
	}
	if opts.src == "" || opts.xcstrings == "" {
		return options{}, errors.New("-src and -xcstrings are required\n" + usage)
	}
	return opts, nil
}

func build(opts options) (map[string][]byte, []analyze.Diag, error) {
	catalogs, err := locale.LoadDir(opts.src)
	if err != nil {
		return nil, nil, fmt.Errorf("load locales: %w", err)
	}
	meta, err := locale.LoadMeta(filepath.Join(opts.src, locale.MetaFile))
	if err != nil {
		return nil, nil, fmt.Errorf("load meta: %w", err)
	}
	model, diags := analyze.Analyze(catalogs, opts.defaultLang, meta)
	if analyze.HasErrors(diags) {
		return nil, diags, nil
	}
	return map[string][]byte{opts.xcstrings: xcstrings.Generate(model)}, diags, nil
}

func write(outputs map[string][]byte, stdout io.Writer) error {
	for _, path := range slices.Sorted(maps.Keys(outputs)) {
		if err := os.MkdirAll(filepath.Dir(path), 0o750); err != nil {
			return fmt.Errorf("create directory for %s: %w", path, err)
		}
		if err := os.WriteFile(path, outputs[path], 0o600); err != nil {
			return fmt.Errorf("write %s: %w", path, err)
		}
		fmt.Fprintln(stdout, "wrote", path)
	}
	return nil
}

func compare(outputs map[string][]byte) error {
	var stale []string
	for _, path := range slices.Sorted(maps.Keys(outputs)) {
		current, err := os.ReadFile(path)
		if err != nil && !errors.Is(err, fs.ErrNotExist) {
			return fmt.Errorf("read %s: %w", path, err)
		}
		if !bytes.Equal(current, outputs[path]) {
			stale = append(stale, path)
		}
	}
	if len(stale) > 0 {
		return fmt.Errorf("out of date: %s (run l10n generate)", strings.Join(stale, ", "))
	}
	return nil
}
