ASSETS := ../../ios/LocateDo/Assets.xcassets
WATCH_ASSETS := ../../ios/LocateDoWatch/Assets.xcassets
ANDROID := ../../android/app/src
PLAY := ../../android/fastlane/metadata/android
PREVIEW_DIR := preview
SITE := ../../site

.PHONY: generate android preview favicon

generate:
	swift render.swift $(ASSETS)/AppIcon.appiconset
	swift render.swift $(ASSETS)/AppIcon-Dev.appiconset --variant Dev
	swift render.swift $(ASSETS)/AppIcon-Stg.appiconset --variant Stg
	swift render.swift $(WATCH_ASSETS)/AppIcon.appiconset --watch

android:
	swift render.swift $(ANDROID)/main/res --android
	swift render.swift $(ANDROID)/debug/res --android --variant Dev
	swift render.swift $(ANDROID)/staging/res --android --variant Stg
	swift render.swift $(PLAY)/en-US/images --play
	oxipng -o 4 --strip safe -q $(ANDROID)/*/res/mipmap-*/ic_launcher_*.png $(PLAY)/en-US/images/icon.png
	cp $(PLAY)/en-US/images/icon.png $(PLAY)/ja-JP/images/icon.png

preview:
	swift render.swift $(PREVIEW_DIR) --preview
	swift render.swift $(PREVIEW_DIR) --preview --variant Dev
	swift render.swift $(PREVIEW_DIR) --preview --variant Stg

favicon:
	swift render.swift $(SITE) --favicon
