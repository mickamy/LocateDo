import CoreLocation
import XCTest

// Run through `fastlane screenshots`, which erases the simulator and grants "Always" first. Region
// monitoring is unsupported in the simulator, and the completion notice comes from the server, so both
// notifications come from debug actions.
final class ScreenshotTests: XCTestCase {
    private let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")

    override func setUp() {
        continueAfterFailure = false
    }

    @MainActor
    func testScreenshots() throws {
        let app = XCUIApplication()
        // Snapshot.deviceLanguage is only set once setupSnapshot has run.
        setupSnapshot(app)
        let seed = Seed(japanese: Snapshot.deviceLanguage.hasPrefix("ja"))
        XCUIDevice.shared.location = XCUILocation(location: seed.center)
        app.launchArguments += ["-SeedScreenshotData"]
        app.launch()
        finishOnboarding(app)

        let grocery = app.staticTexts[seed.groceryName].firstMatch
        XCTAssertTrue(grocery.waitForExistence(timeout: 10))
        snapshot("04-Nearby")

        grocery.tap()
        XCTAssertTrue(app.staticTexts[seed.firstTodo].waitForExistence(timeout: 5))
        snapshot("03-Place")
        app.navigationBars.buttons.element(boundBy: 0).tap()

        app.buttons["home.map"].tap()
        sleep(3)
        snapshot("05-Map")
        app.navigationBars.buttons.element(boundBy: 0).tap()

        app.buttons["home.allTodos"].tap()
        XCTAssertTrue(app.staticTexts[seed.firstTodo].waitForExistence(timeout: 5))
        snapshot("06-Todos")

        // Last, the lock screen: each debug action clears what was delivered before, so one notification shows.
        app.navigationBars.buttons.element(boundBy: 0).tap()
        grocery.tap()
        catchOnLockScreen(app, action: "Simulate completion notice in 10 s (debug)", containing: seed.partnerName)
        snapshot("02-Checked")

        XCUIDevice.shared.press(.home)
        app.activate()
        XCTAssertTrue(app.buttons["place.menu"].waitForExistence(timeout: 10))
        catchOnLockScreen(app, action: "Simulate arrival in 10 s (debug)", containing: seed.groceryName)
        snapshot("01-Arrival")
    }

    @MainActor
    private func catchOnLockScreen(_ app: XCUIApplication, action: String, containing text: String) {
        app.buttons["place.menu"].tap()
        app.buttons[action].tap()
        XCUIDevice.shared.perform(NSSelectorFromString("pressLockButton"))
        let banner = springboard.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", text))
            .firstMatch
        XCTAssertTrue(banner.waitForExistence(timeout: 30))
        sleep(1)
    }

    @MainActor
    private func finishOnboarding(_ app: XCUIApplication) {
        let start = app.buttons["onboarding.start"]
        XCTAssertTrue(start.waitForExistence(timeout: 10))
        // The intro reveals itself top to bottom; the button fades in last.
        sleep(1)
        // "Always" is granted before launch, so onboarding skips location, and only the notification alert appears,
        // asked from Home's banner.
        start.tap()
        let later = app.buttons["onboarding.later"]
        XCTAssertTrue(later.waitForExistence(timeout: 5))
        later.tap()
        let banner = app.buttons["home.permissionBanner"]
        XCTAssertTrue(banner.waitForExistence(timeout: 5))
        banner.tap()
        allowSystemAlert()
    }

    // The notification alert reads "Don't Allow / Allow". On a freshly erased simulator it can take
    // more than 10 seconds to appear.
    @MainActor
    private func allowSystemAlert() {
        let alert = springboard.alerts.firstMatch
        XCTAssertTrue(alert.waitForExistence(timeout: 60))
        alert.buttons.element(boundBy: 1).tap()
    }
}

private struct Seed {
    let center: CLLocation
    let groceryName: String
    let firstTodo: String
    let partnerName: String

    // Matches ScreenshotSeed in the app.
    init(japanese: Bool) {
        if japanese {
            center = CLLocation(latitude: 35.67824, longitude: 139.76712)
            groceryName = "スーパー"
            firstTodo = "牛乳"
            partnerName = "ゆき"
        } else {
            center = CLLocation(latitude: 37.77627, longitude: -122.41924)
            groceryName = "Grocery store"
            firstTodo = "Milk"
            partnerName = "Alex"
        }
    }
}
