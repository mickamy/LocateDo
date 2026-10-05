import CoreLocation
import XCTest

// Run through `fastlane screenshots`, which erases the simulator and grants "Always" first. Region
// monitoring is unsupported in the simulator, so the arrival notification comes from the debug
// "Simulate arrival in 10 s" action.
final class ScreenshotTests: XCTestCase {
    private let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")

    override func setUp() {
        continueAfterFailure = false
    }

    @MainActor
    func testScreenshots() throws {
        let japanese = Snapshot.deviceLanguage.hasPrefix("ja")
        let seed = Seed(japanese: japanese)
        XCUIDevice.shared.location = XCUILocation(location: seed.center)

        let app = XCUIApplication()
        setupSnapshot(app)
        app.launchArguments += ["-SeedScreenshotData"]
        app.launch()
        finishOnboarding(app)

        let grocery = app.staticTexts[seed.groceryName].firstMatch
        XCTAssertTrue(grocery.waitForExistence(timeout: 10))
        snapshot("02-Nearby")

        grocery.tap()
        XCTAssertTrue(app.staticTexts[seed.firstTodo].waitForExistence(timeout: 5))
        snapshot("03-Place")
        app.navigationBars.buttons.element(boundBy: 0).tap()

        app.tabBars.buttons.element(boundBy: 1).tap()
        sleep(3)
        snapshot("04-Map")

        app.tabBars.buttons.element(boundBy: 2).tap()
        XCTAssertTrue(app.staticTexts[seed.firstTodo].waitForExistence(timeout: 5))
        snapshot("05-Todos")

        // Last, so the device stays locked: schedule the arrival, lock, and catch it on the lock screen.
        app.tabBars.buttons.element(boundBy: 0).tap()
        grocery.tap()
        app.buttons["place.menu"].tap()
        app.buttons["Simulate arrival in 10 s (debug)"].tap()
        XCUIDevice.shared.perform(NSSelectorFromString("pressLockButton"))
        let banner = springboard.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", seed.groceryName))
            .firstMatch
        XCTAssertTrue(banner.waitForExistence(timeout: 30))
        sleep(1)
        snapshot("01-Arrival")
    }

    @MainActor
    private func finishOnboarding(_ app: XCUIApplication) {
        let start = app.buttons["onboarding.start"]
        XCTAssertTrue(start.waitForExistence(timeout: 10))
        // "Always" is granted before launch, so only the notification alert appears.
        start.tap()
        let allowNotifications = app.buttons["onboarding.allowNotifications"]
        XCTAssertTrue(allowNotifications.waitForExistence(timeout: 5))
        allowNotifications.tap()
        allowSystemAlert()
    }

    // The notification alert reads "Don't Allow / Allow".
    @MainActor
    private func allowSystemAlert() {
        let alert = springboard.alerts.firstMatch
        XCTAssertTrue(alert.waitForExistence(timeout: 10))
        alert.buttons.element(boundBy: 1).tap()
    }
}

private struct Seed {
    let center: CLLocation
    let groceryName: String
    let firstTodo: String

    // Matches ScreenshotSeed in the app.
    init(japanese: Bool) {
        if japanese {
            center = CLLocation(latitude: 35.6437, longitude: 139.6710)
            groceryName = "スーパー"
            firstTodo = "牛乳"
        } else {
            center = CLLocation(latitude: 37.3230, longitude: -122.0322)
            groceryName = "Grocery store"
            firstTodo = "Milk"
        }
    }
}
