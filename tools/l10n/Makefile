BUILD_DIR = bin
SRC = ../../shared/strings
XCSTRINGS = ../../ios/LocateDo/Localizable.xcstrings
INFOPLIST = ../../ios/LocateDo/InfoPlist.xcstrings
ANDROID_RES = ../../android/app/src/main/res

.PHONY: build \
		clean \
		test \
		lint \
		lint-fix \
		generate \
		check \

build:
	@mkdir -p $(BUILD_DIR)
	go build -o $(BUILD_DIR)/l10n .

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
	go run . generate -src $(SRC) -xcstrings $(XCSTRINGS) -infoplist $(INFOPLIST) -android $(ANDROID_RES)

check:
	go run . check -src $(SRC) -xcstrings $(XCSTRINGS) -infoplist $(INFOPLIST) -android $(ANDROID_RES)
