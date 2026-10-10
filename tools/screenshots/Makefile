PLAY := ../../android/fastlane/metadata/android

.PHONY: feature-graphic

feature-graphic:
	swift feature_graphic.swift $(PLAY)/en-US/images/icon.png $(PLAY)/en-US/images/featureGraphic.png \
		"StopBy" "Shared shopping list by place" --language en
	swift feature_graphic.swift $(PLAY)/ja-JP/images/icon.png $(PLAY)/ja-JP/images/featureGraphic.png \
		"ここメモ" "もう買い忘れない。家族とリストを共有" --language ja
	oxipng -o 4 --strip safe -q $(PLAY)/*/images/featureGraphic.png
