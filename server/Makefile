include .env
export ENV MODULE_ROOT DATABASE_URL DATABASE_WRITER_URL DATABASE_READER_URL JWT_SIGNING_KEY SEAL_KEY

BUILD_DIR = bin
GOOSE = go tool -modfile=tools/go.mod goose -dir internal/infra/storage/db/migrate/sql
DB_USER = admin
DB_NAME = locatedo

.PHONY: build \
		clean \
		test \
		lint \
		lint-fix \
		compose-up \
		compose-up-d \
		compose-down \
		compose-down-v \
		db-create \
		db-drop \
		db-migrate \
		db-reset \
		gen \
		gen-go \
		gen-di \
		gen-mapper \
		gen-sqlc \
		new-migration \

.env:
	JWT_SIGNING_KEY=$$(openssl rand -base64 48) \
	SEAL_KEY=$$(openssl rand -base64 32) \
	envsubst < .env.example > $@

build:
	@mkdir -p $(BUILD_DIR)
	go build -o $(BUILD_DIR)/api ./cmd/api
	go build -o $(BUILD_DIR)/worker ./cmd/worker

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

compose-up:
	docker compose up

compose-up-d:
	docker compose up -d --wait

compose-down:
	docker compose down

compose-down-v:
	docker compose down -v

db-create:
	docker compose exec -T db psql -U $(DB_USER) -d postgres -tAc \
		"SELECT 1 FROM pg_database WHERE datname = '$(DB_NAME)'" | grep -q 1 || \
		docker compose exec -T db createdb -U $(DB_USER) $(DB_NAME)

db-drop:
	docker compose exec -T db dropdb -U $(DB_USER) --force --if-exists $(DB_NAME)

db-migrate:
	$(GOOSE) postgres "$(DATABASE_URL)" up

db-reset: db-drop db-create db-migrate

gen: gen-go gen-sqlc gen-mapper

gen-go:
	go generate ./...

gen-di:
	go tool -modfile=tools/go.mod kanna-di ./config ./internal/...

gen-mapper:
	go tool -modfile=tools/go.mod kanna-mapper -converters=./internal/lib/converters -direction=to \
		-types=github.com/mickamy/LocateDo/internal/feature/account/model.Session:*github.com/mickamy/LocateDo/internal/gen/locatedo/account/v1.Session \
		-destination=internal/feature/account/mapper -package=mapper

gen-sqlc:
	go tool -modfile=tools/go.mod sqlc generate

new-migration:
	@if [ -z "$(name)" ]; then \
		echo "Error: name is required. Usage: make new-migration name=migration_name"; \
		exit 1; \
	fi
	$(GOOSE) create $(name) sql
