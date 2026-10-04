APPICONSET := ../../ios/LocateDo/Assets.xcassets/AppIcon.appiconset
PREVIEW_DIR := preview

.PHONY: generate preview

generate:
	swift render.swift $(APPICONSET)

preview:
	swift render.swift $(PREVIEW_DIR) --preview
