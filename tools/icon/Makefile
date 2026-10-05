ASSETS := ../../ios/LocateDo/Assets.xcassets
PREVIEW_DIR := preview
SITE := ../../docs/legal

.PHONY: generate preview favicon

generate:
	swift render.swift $(ASSETS)/AppIcon.appiconset
	swift render.swift $(ASSETS)/AppIcon-Dev.appiconset --variant Dev
	swift render.swift $(ASSETS)/AppIcon-Stg.appiconset --variant Stg

preview:
	swift render.swift $(PREVIEW_DIR) --preview
	swift render.swift $(PREVIEW_DIR) --preview --variant Dev
	swift render.swift $(PREVIEW_DIR) --preview --variant Stg

favicon:
	swift render.swift $(SITE) --favicon
