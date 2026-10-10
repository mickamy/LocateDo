import CoreLocation
import XCTest

// Drives the App Store app preview for `fastlane app_preview`, which records each scene on its own. A scene prints
// "PREVIEW <scene> start" and waits until the lane has started recording, and prints "PREVIEW <scene> end" and waits
// until it has stopped; the lane answers with files in TEST_RUNNER_PREVIEW_SIGNALS. The language comes from
// TEST_RUNNER_PREVIEW_LANGUAGE.
final class AppPreviewTests: XCTestCase {
    private let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")

    override func setUp() {
        continueAfterFailure = false
    }

    @MainActor
    func testPreview() throws {
        let japanese = ProcessInfo.processInfo.environment["PREVIEW_LANGUAGE"] == "ja"
        let seed = PreviewSeed(japanese: japanese)
        XCUIDevice.shared.location = XCUILocation(location: seed.center)
        let app = launchApp(japanese: japanese)
        allowNotifications(app)

        // The screen recording does not reliably catch the notification landing on the lock screen, so this scene
        // is a screenshot the lane holds. The home button then unlocks a simulator without a passcode, back on the
        // place.
        let grocery = app.staticTexts[seed.groceryName].firstMatch
        grocery.tapWhenReady()
        app.buttons["place.menu"].tapWhenReady()
        app.buttons["Simulate arrival in 10 s (debug)"].tapWhenReady()
        XCUIDevice.shared.perform(NSSelectorFromString("pressLockButton"))
        let banner = springboard.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", seed.groceryName))
            .firstMatch
        XCTAssertTrue(banner.waitForExistence(timeout: 30))
        pause(1)
        still("arrival")
        XCUIDevice.shared.press(.home)
        app.activate()

        XCTAssertTrue(app.buttons[seed.firstTodo].waitForExistence(timeout: 10))
        scene("place") {
            pause(1)
            toggle(app.buttons[seed.firstTodo])
            pause(2)
            toggle(app.buttons[seed.secondTodo])
            pause(2)
        }

        app.navigationBars.buttons.element(boundBy: 0).tapWhenReady()
        XCTAssertTrue(grocery.waitForExistence(timeout: 5))
        app.buttons["home.map"].tapWhenReady()
        pause(2)
        scene("map") {
            pause(4)
        }

        app.navigationBars.buttons.element(boundBy: 0).tapWhenReady()
        app.buttons["home.allTodos"].tapWhenReady()
        pause(1)
        scene("todos") {
            pause(2)
            app.segmentedControls.buttons[seed.openFilter].tapWhenReady()
            pause(3)
        }

        app.navigationBars.buttons.element(boundBy: 0).tapWhenReady()
        XCTAssertTrue(grocery.waitForExistence(timeout: 5))
        scene("home") {
            pause(4)
        }
    }

    private func launchApp(japanese: Bool) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments += ["-SeedScreenshotData", "-completedOnboarding", "YES"]
        if japanese {
            app.launchArguments += ["-AppleLanguages", "(ja)", "-AppleLocale", "ja_JP"]
        } else {
            app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        }
        app.launch()
        return app
    }

    private func scene(_ name: String, _ body: () -> Void) {
        tellLane("PREVIEW \(name) start")
        waitForLane("\(name).recording")
        body()
        tellLane("PREVIEW \(name) end")
        waitForLane("\(name).stopped")
    }

    // Saved next to the lane's signals as <name>.still.png, which the lane shows in place of a recording.
    private func still(_ name: String) {
        let folder = ProcessInfo.processInfo.environment["PREVIEW_SIGNALS"] ?? ""
        let url = URL(fileURLWithPath: folder).appendingPathComponent("\(name).still.png")
        do {
            try XCUIScreen.main.screenshot().pngRepresentation.write(to: url)
        } catch {
            XCTFail("Could not save the \(name) screenshot: \(error)")
        }
    }

    // xcodebuild passes the line on as soon as it is flushed.
    private func tellLane(_ line: String) {
        print(line)
        fflush(stdout)
    }

    private func waitForLane(_ file: String) {
        let folder = ProcessInfo.processInfo.environment["PREVIEW_SIGNALS"] ?? ""
        let path = URL(fileURLWithPath: folder).appendingPathComponent(file).path
        let deadline = Date().addingTimeInterval(30)
        while !FileManager.default.fileExists(atPath: path) {
            guard Date() < deadline else {
                XCTFail("The lane never wrote \(file)")
                return
            }
            Thread.sleep(forTimeInterval: 0.1)
        }
    }

    // A to-do row reads as one button; its checkbox sits at the leading edge.
    @MainActor
    private func toggle(_ row: XCUIElement) {
        XCTAssertTrue(row.waitForExistence(timeout: 5))
        row.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: 12, dy: row.frame.height / 2)).tap()
    }

    // Off camera, before the arrival. The alert can take more than 10 seconds to appear on a freshly erased
    // simulator.
    @MainActor
    private func allowNotifications(_ app: XCUIApplication) {
        app.buttons["home.settings"].tapWhenReady()
        app.buttons["settings.allowNotifications"].tapWhenReady()
        let alert = springboard.alerts.firstMatch
        XCTAssertTrue(alert.waitForExistence(timeout: 60))
        alert.buttons.element(boundBy: 1).tap()
        XCTAssertTrue(app.buttons["settings.allowNotifications"].waitForNonExistence(timeout: 10))
        app.buttons["settings.done"].tapWhenReady()
    }

    private func pause(_ seconds: UInt32) {
        sleep(seconds)
    }
}

private struct PreviewSeed {
    let center: CLLocation
    let groceryName: String
    let firstTodo: String
    let secondTodo: String
    let openFilter: String

    // Matches ScreenshotSeed in the app.
    init(japanese: Bool) {
        if japanese {
            center = CLLocation(latitude: 35.67824, longitude: 139.76712)
            groceryName = "スーパー"
            firstTodo = "牛乳"
            secondTodo = "卵"
            openFilter = "未完了"
        } else {
            center = CLLocation(latitude: 37.77627, longitude: -122.41924)
            groceryName = "Grocery store"
            firstTodo = "Milk"
            secondTodo = "Eggs"
            openFilter = "Open"
        }
    }
}

private extension XCUIElement {
    @MainActor
    func tapWhenReady(timeout: TimeInterval = 10) {
        XCTAssertTrue(waitForExistence(timeout: timeout), "\(self) did not appear")
        tap()
    }
}
