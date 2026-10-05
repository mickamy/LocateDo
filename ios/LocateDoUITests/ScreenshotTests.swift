import CoreLocation
import XCTest

// Run through `fastlane screenshots`; grant "Always" location to the app first so the arrival fires while it is closed.
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

        app.tabBars.buttons.element(boundBy: 0).tap()
        XCUIDevice.shared.press(.home)
        sleep(2)
        XCUIDevice.shared.location = XCUILocation(location: seed.grocery)
        let banner = springboard.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", seed.groceryName))
            .firstMatch
        XCTAssertTrue(banner.waitForExistence(timeout: 90))
        snapshot("01-Arrival")
    }

    @MainActor
    private func finishOnboarding(_ app: XCUIApplication) {
        let start = app.buttons["onboarding.start"]
        guard start.waitForExistence(timeout: 5) else {
            return
        }
        start.tap()
        allowSystemAlert(atIndex: 1)
        let allowNotifications = app.buttons["onboarding.allowNotifications"]
        XCTAssertTrue(allowNotifications.waitForExistence(timeout: 5))
        allowNotifications.tap()
        allowSystemAlert(atIndex: 1)
    }

    // Location alerts read "Allow Once / Allow While Using App / Don't Allow" and notification alerts
    // "Don't Allow / Allow", so index 1 allows either way.
    @MainActor
    private func allowSystemAlert(atIndex index: Int) {
        let alert = springboard.alerts.firstMatch
        if alert.waitForExistence(timeout: 3) {
            alert.buttons.element(boundBy: index).tap()
        }
    }
}

private struct Seed {
    let center: CLLocation
    let grocery: CLLocation
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
        grocery = CLLocation(latitude: center.coordinate.latitude + 0.0030, longitude: center.coordinate.longitude)
    }
}
