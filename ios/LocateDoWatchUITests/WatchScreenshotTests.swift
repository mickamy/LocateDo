import XCTest

// Run through `fastlane watch_screenshots`, which sets SCREENSHOT_LANGUAGE and exports the attachments.
// The texts below are WatchScreenshotSeed's.
final class WatchScreenshotTests: XCTestCase {
    private var japanese: Bool {
        ProcessInfo.processInfo.environment["SCREENSHOT_LANGUAGE"] == "ja"
    }

    override func setUp() {
        continueAfterFailure = false
    }

    @MainActor
    func testScreenshots() throws {
        let firstTodo = japanese ? "牛乳" : "Milk"

        let arrival = launch(["-ShowArrivalNotification"])
        let arrivalTodo = arrival.buttons.containing(.staticText, identifier: firstTodo).firstMatch
        XCTAssertTrue(arrivalTodo.waitForExistence(timeout: 10))
        arrivalTodo.tap()
        sleep(1)
        attachScreenshot("01-Arrival")
        arrival.terminate()

        let app = launch([])
        let grocery = app.staticTexts[japanese ? "スーパー" : "Grocery store"].firstMatch
        XCTAssertTrue(grocery.waitForExistence(timeout: 10))
        sleep(1)
        attachScreenshot("02-Places")

        grocery.tap()
        let todo = app.buttons.containing(.staticText, identifier: firstTodo).firstMatch
        XCTAssertTrue(todo.waitForExistence(timeout: 5))
        todo.tap()
        sleep(1)
        attachScreenshot("03-Place")
    }

    @MainActor
    private func launch(_ arguments: [String]) -> XCUIApplication {
        let app = XCUIApplication()
        let language = japanese ? "ja" : "en"
        let locale = japanese ? "ja_JP" : "en_US"
        app.launchArguments = ["-SeedScreenshotData", "-AppleLanguages", "(\(language))", "-AppleLocale", locale]
            + arguments
        app.launch()
        return app
    }

    @MainActor
    private func attachScreenshot(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
