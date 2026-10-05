import CoreLocation
import XCTest

// Drives the arrival flow for the App Review video, in English on a fresh simulator, while
// `xcrun simctl io <device> recordVideo` records the screen.
final class ArrivalVideoTests: XCTestCase {
    private static let store = CLLocation(latitude: 37.3260, longitude: -122.0322)
    private static let home = CLLocation(latitude: 37.3080, longitude: -122.0322)

    private let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")

    override func setUp() {
        continueAfterFailure = false
    }

    @MainActor
    func testArrivalWhileClosed() throws {
        XCUIDevice.shared.location = XCUILocation(location: Self.store)
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()

        app.buttons["onboarding.start"].tapWhenReady()
        allowSystemAlert(atIndex: 1)
        app.buttons["onboarding.allowNotifications"].tapWhenReady()
        allowSystemAlert(atIndex: 1)

        app.buttons["Add a place"].tapWhenReady()
        let name = app.textFields.firstMatch
        name.tapWhenReady()
        name.typeText("Grocery store")
        app.buttons["Choose on map"].tapWhenReady()
        app.buttons["Use current location"].tapWhenReady()
        app.buttons["Use this location"].tapWhenReady()
        pause(1)
        app.buttons["Save"].tapWhenReady()

        app.buttons["Allow “Always”"].tapWhenReady()
        allowSystemAlert(atIndex: 1)
        pause(1)

        app.staticTexts["Grocery store"].tapWhenReady()
        app.buttons["Add a to-do"].tapWhenReady()
        let title = app.textFields.firstMatch
        title.tapWhenReady()
        title.typeText("Buy milk")
        app.buttons["Save"].tapWhenReady()
        pause(2)

        XCUIDevice.shared.location = XCUILocation(location: Self.home)
        pause(2)
        app.terminate()
        pause(3)

        XCUIDevice.shared.location = XCUILocation(location: Self.store)
        let banner = springboard.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", "Grocery store"))
            .firstMatch
        XCTAssertTrue(banner.waitForExistence(timeout: 120))
        pause(3)
        banner.tap()
        XCTAssertTrue(app.staticTexts["Buy milk"].waitForExistence(timeout: 10))
        pause(3)
    }

    // "Allow Once / Allow While Using App / Don't Allow", "Don't Allow / Allow", and
    // "Keep Only While Using / Change to Always Allow" all allow at index 1.
    @MainActor
    private func allowSystemAlert(atIndex index: Int) {
        let alert = springboard.alerts.firstMatch
        if alert.waitForExistence(timeout: 5) {
            pause(1)
            alert.buttons.element(boundBy: index).tap()
        }
    }

    // Leaves time for the viewer to follow each step.
    private func pause(_ seconds: UInt32) {
        sleep(seconds)
    }
}

private extension XCUIElement {
    @MainActor
    func tapWhenReady(timeout: TimeInterval = 10) {
        XCTAssertTrue(waitForExistence(timeout: timeout), "\(self) did not appear")
        tap()
    }
}
