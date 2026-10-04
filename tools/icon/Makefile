APPICONSET := ../../ios/LocateDo/Assets.xcassets/AppIcon.appiconset
DEV_APPICONSET := ../../ios/LocateDo/Assets.xcassets/AppIcon-Dev.appiconset
PREVIEW_DIR := preview

.PHONY: generate preview

generate:
	swift render.swift $(APPICONSET)
	swift render.swift $(DEV_APPICONSET) --dev

preview:
	swift render.swift $(PREVIEW_DIR) --preview
	swift render.swift $(PREVIEW_DIR) --preview --dev
