import CoreLocation
import XCTest

// Drives the App Store app preview while `fastlane app_preview` records the screen. Each scene prints
// "PREVIEW <scene> start|end <time>", and the lane cuts the recording at those lines and puts the scenes
// in story order, so they are shot here in whatever order is easiest. The language comes from
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
        let app = XCUIApplication()
        app.launchArguments += ["-SeedScreenshotData", "-completedOnboarding", "YES"]
        if japanese {
            app.launchArguments += ["-AppleLanguages", "(ja)", "-AppleLocale", "ja_JP"]
        } else {
            app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        }
        app.launch()
        allowNotifications(app)

        // The arrival goes first so the notification still lists every to-do; the lock screen is left with
        // the home button, which unlocks a simulator without a passcode.
        let grocery = app.staticTexts[seed.groceryName].firstMatch
        grocery.tapWhenReady()
        app.buttons["place.menu"].tapWhenReady()
        app.buttons["Simulate arrival in 10 s (debug)"].tapWhenReady()
        XCUIDevice.shared.perform(NSSelectorFromString("pressLockButton"))
        let banner = springboard.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", seed.groceryName))
            .firstMatch
        XCTAssertTrue(banner.waitForExistence(timeout: 30))
        scene("arrival") {
            pause(4)
        }
        XCUIDevice.shared.press(.home)
        app.activate()

        XCTAssertTrue(app.buttons[seed.firstTodo].waitForExistence(timeout: 10))
        scene("place") {
            pause(1)
            toggle(app.buttons[seed.firstTodo])
            pause(1)
            toggle(app.buttons[seed.secondTodo])
            pause(2)
        }

        app.navigationBars.buttons.element(boundBy: 0).tapWhenReady()
        XCTAssertTrue(grocery.waitForExistence(timeout: 5))
        scene("home") {
            pause(4)
        }

        app.tabBars.buttons.element(boundBy: 1).tapWhenReady()
        pause(2)
        scene("map") {
            pause(4)
        }

        app.tabBars.buttons.element(boundBy: 2).tapWhenReady()
        pause(1)
        scene("todos") {
            pause(4)
        }
    }

    private func scene(_ name: String, _ body: () -> Void) {
        print("PREVIEW \(name) start \(Date().timeIntervalSince1970)")
        body()
        print("PREVIEW \(name) end \(Date().timeIntervalSince1970)")
    }

    // A to-do row reads as one button; its checkbox sits at the leading edge.
    @MainActor
    private func toggle(_ row: XCUIElement) {
        XCTAssertTrue(row.waitForExistence(timeout: 5))
        row.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: 12, dy: row.frame.height / 2)).tap()
    }

    // Off camera, so the lock screen can show the notification. The alert can take more than 10 seconds
    // to appear on a freshly erased simulator.
    @MainActor
    private func allowNotifications(_ app: XCUIApplication) {
        app.tabBars.buttons.element(boundBy: 3).tapWhenReady()
        app.buttons["settings.allowNotifications"].tapWhenReady()
        let alert = springboard.alerts.firstMatch
        XCTAssertTrue(alert.waitForExistence(timeout: 60))
        alert.buttons.element(boundBy: 1).tap()
        XCTAssertTrue(app.buttons["settings.allowNotifications"].waitForNonExistence(timeout: 10))
        app.tabBars.buttons.element(boundBy: 0).tapWhenReady()
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

    // Matches ScreenshotSeed in the app.
    init(japanese: Bool) {
        if japanese {
            center = CLLocation(latitude: 35.67824, longitude: 139.76712)
            groceryName = "スーパー"
            firstTodo = "牛乳"
            secondTodo = "卵"
        } else {
            center = CLLocation(latitude: 37.77627, longitude: -122.41924)
            groceryName = "Grocery store"
            firstTodo = "Milk"
            secondTodo = "Eggs"
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
