BUILD_DIR = bin
CATALOG = ../../../shared/analytics/catalog.yaml
SWIFT = ../../../ios/LocateDo/Services/Analytics/AnalyticsCatalog.swift
KOTLIN = ../../../android/app/src/main/java/com/locatedo/locatedo/core/analytics/AnalyticsCatalog.kt
KOTLIN_PACKAGE = com.locatedo.locatedo.core.analytics
JSON = ../catalog.json
SQL = ../bigquery/00_events.sql
FLAGS = -catalog $(CATALOG) -swift $(SWIFT) -kotlin $(KOTLIN) -kotlin-package $(KOTLIN_PACKAGE) -json $(JSON) -sql $(SQL)

.PHONY: build \
		clean \
		test \
		lint \
		lint-fix \
		generate \
		check \

build:
	@mkdir -p $(BUILD_DIR)
	go build -o $(BUILD_DIR)/catalog .

clean:
	rm -rf $(BUILD_DIR)

test:
	go test ./... -race

lint:
	@command -v golangci-lint >/dev/null 2>&1 || { \
		echo "golangci-lint is not installed"; \
		exit 1; \
	}
	golangci-lint run

lint-fix:
	@command -v golangci-lint >/dev/null 2>&1 || { \
		echo "golangci-lint is not installed"; \
		exit 1; \
	}
	golangci-lint run --fix

generate:
	go run . generate $(FLAGS)

check:
	go run . check $(FLAGS)
