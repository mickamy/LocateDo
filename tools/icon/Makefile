ASSETS := ../../ios/LocateDo/Assets.xcassets
PREVIEW_DIR := preview

.PHONY: generate preview

generate:
	swift render.swift $(ASSETS)/AppIcon.appiconset
	swift render.swift $(ASSETS)/AppIcon-Dev.appiconset --variant Dev
	swift render.swift $(ASSETS)/AppIcon-Stg.appiconset --variant Stg

preview:
	swift render.swift $(PREVIEW_DIR) --preview
	swift render.swift $(PREVIEW_DIR) --preview --variant Dev
	swift render.swift $(PREVIEW_DIR) --preview --variant Stg
