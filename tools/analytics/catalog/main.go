package main

import (
	"bytes"
	"errors"
	"flag"
	"fmt"
	"io"
	"os"
	"path/filepath"

	"github.com/mickamy/LocateDo/tools/analytics/catalog/internal/catalog"
)

const usage = "usage: catalog <generate|check> -catalog <path> -swift <path> -kotlin <path> -kotlin-package <name>" +
	" -json <path> -sql <path>"

type options struct {
	catalog       string
	swift         string
	kotlin        string
	kotlinPackage string
	json          string
	sql           string
}

type output struct {
	path string
	data []byte
}

func main() {
	if err := run(os.Args[1:], os.Stdout, os.Stderr); err != nil {
		fmt.Fprintln(os.Stderr, "catalog:", err)
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
	outputs, err := build(opts)
	if err != nil {
		return err
	}
	if cmd == "check" {
		return compare(outputs)
	}
	return write(outputs, stdout)
}

func parseFlags(cmd string, args []string, stderr io.Writer) (options, error) {
	fs := flag.NewFlagSet("catalog "+cmd, flag.ContinueOnError)
	fs.SetOutput(stderr)
	var opts options
	fs.StringVar(&opts.catalog, "catalog", "", "catalog.yaml to read")
	fs.StringVar(&opts.swift, "swift", "", "Swift file to generate")
	fs.StringVar(&opts.kotlin, "kotlin", "", "Kotlin file to generate")
	fs.StringVar(&opts.kotlinPackage, "kotlin-package", "", "package of the Kotlin file")
	fs.StringVar(&opts.json, "json", "", "catalog JSON for the Python tools to generate")
	fs.StringVar(&opts.sql, "sql", "", "BigQuery events view to generate")
	if err := fs.Parse(args); err != nil {
		return options{}, fmt.Errorf("parse flags: %w", err)
	}
	if opts.catalog == "" || opts.swift == "" || opts.kotlin == "" || opts.kotlinPackage == "" || opts.json == "" ||
		opts.sql == "" {
		return options{}, errors.New(usage)
	}
	return opts, nil
}

func build(opts options) ([]output, error) {
	c, err := catalog.Load(opts.catalog)
	if err != nil {
		return nil, fmt.Errorf("load %s: %w", opts.catalog, err)
	}
	jsonData, err := c.JSON()
	if err != nil {
		return nil, fmt.Errorf("render catalog json: %w", err)
	}
	return []output{
		{opts.swift, c.Swift()},
		{opts.kotlin, c.Kotlin(opts.kotlinPackage)},
		{opts.json, jsonData},
		{opts.sql, c.SQL()},
	}, nil
}

func compare(outputs []output) error {
	var stale []error
	for _, o := range outputs {
		current, err := os.ReadFile(o.path)
		if err != nil && !errors.Is(err, os.ErrNotExist) {
			return fmt.Errorf("read %s: %w", o.path, err)
		}
		if !bytes.Equal(current, o.data) {
			stale = append(stale, fmt.Errorf("%s is out of date; run make -C tools/analytics/catalog generate", o.path))
		}
	}
	return errors.Join(stale...)
}

func write(outputs []output, stdout io.Writer) error {
	for _, o := range outputs {
		if err := os.MkdirAll(filepath.Dir(o.path), 0o750); err != nil {
			return fmt.Errorf("create directory for %s: %w", o.path, err)
		}
		if err := os.WriteFile(o.path, o.data, 0o600); err != nil {
			return fmt.Errorf("write %s: %w", o.path, err)
		}
		fmt.Fprintln(stdout, "wrote", o.path)
	}
	return nil
}
