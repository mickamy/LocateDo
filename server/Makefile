include .env
export ENV MODULE_ROOT DATABASE_URL DATABASE_WRITER_URL DATABASE_READER_URL JWT_SIGNING_KEY SEAL_KEY

BUILD_DIR = bin
GOOSE = go tool -modfile=tools/go.mod goose -dir internal/infra/storage/db/migrate/sql
MAPPER = go tool -modfile=tools/go.mod kanna-mapper -converters=./internal/lib/converters
PKG = github.com/mickamy/LocateDo/internal
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
		gen-fixture \
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
	$(MAPPER) -destination=internal/feature/account/mapper -package=mapper \
		-types='$(PKG)/feature/account/model.Session->*$(PKG)/gen/locatedo/account/v1.Session'
	$(MAPPER) -converters=./internal/feature/household/mapper -converters=./internal/feature/category/mapper \
		-destination=internal/feature/household/mapper -package=mapper \
		-types='$(PKG)/feature/household/model.Contents<-*$(PKG)/gen/locatedo/household/v1.CreateHouseholdRequest' \
		-types='$(PKG)/feature/household/model.ImportCategory<-*$(PKG)/gen/locatedo/category/v1.CategoryInput' \
		-types='$(PKG)/feature/household/model.ImportPlace<-*$(PKG)/gen/locatedo/place/v1.PlaceInput' \
		-types='$(PKG)/feature/household/model.InitialTodo<-*$(PKG)/gen/locatedo/household/v1.InitialTodo' \
		-types='$(PKG)/feature/household/model.ImportTodo<-*$(PKG)/gen/locatedo/todo/v1.TodoInput' \
		-types='$(PKG)/feature/household/model.Household->*$(PKG)/gen/locatedo/household/v1.Household'
	$(MAPPER) -destination=internal/feature/place/mapper -package=mapper -exclude='$(PKG)/feature/place/model.Place.HouseholdID,$(PKG)/feature/place/model.Place.UpdatedAt,$(PKG)/feature/place/model.Place.Version' \
		-types='$(PKG)/feature/place/model.Place<-*$(PKG)/gen/locatedo/place/v1.PlaceInput'
	$(MAPPER) -converters=./internal/feature/category/mapper \
		-destination=internal/feature/category/mapper -package=mapper \
		-exclude='$(PKG)/feature/category/model.Category.HouseholdID,$(PKG)/feature/category/model.Category.UpdatedAt,$(PKG)/feature/category/model.Category.Version' \
		-types='$(PKG)/feature/category/model.Category<-*$(PKG)/gen/locatedo/category/v1.CategoryInput'
	$(MAPPER) -destination=internal/feature/todo/mapper -package=mapper \
		-exclude='$(PKG)/feature/todo/model.Todo.HouseholdID,$(PKG)/feature/todo/model.Todo.CompletedAt,$(PKG)/feature/todo/model.Todo.UpdatedAt,$(PKG)/feature/todo/model.Todo.Version' \
		-types='$(PKG)/feature/todo/model.Todo<-*$(PKG)/gen/locatedo/todo/v1.TodoInput'

gen-sqlc:
	go tool -modfile=tools/go.mod sqlc generate

gen-fixture:
	go tool -modfile=tools/go.mod kanna-fixture -package=fixture \
		-source=./internal/feature/place/model -destination=internal/feature/place/fixture
	go tool -modfile=tools/go.mod kanna-fixture -package=fixture \
		-source=./internal/feature/category/model -destination=internal/feature/category/fixture
	go tool -modfile=tools/go.mod kanna-fixture -package=fixture \
		-source=./internal/feature/todo/model -destination=internal/feature/todo/fixture

new-migration:
	@if [ -z "$(name)" ]; then \
		echo "Error: name is required. Usage: make new-migration name=migration_name"; \
		exit 1; \
	fi
	$(GOOSE) create $(name) sql
