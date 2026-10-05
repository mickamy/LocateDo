import CoreLocation
import XCTest

// Drives the setup for the App Review video on an erased simulator, in English, while it is recorded:
// onboarding, saving a place, granting "Always", adding a to-do, and the permission in Settings.
// Arrival itself is explained in the video, since region monitoring does not run in the simulator.
final class ReviewVideoTests: XCTestCase {
    private let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")

    override func setUp() {
        continueAfterFailure = false
    }

    @MainActor
    func testSetup() throws {
        XCUIDevice.shared.location = XCUILocation(location: CLLocation(latitude: 37.3260, longitude: -122.0322))
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        pause(2)

        app.buttons["onboarding.start"].tapWhenReady()
        allowSystemAlert()
        app.buttons["onboarding.allowNotifications"].tapWhenReady()
        allowSystemAlert()
        pause(2)

        app.buttons["Add a place"].firstMatch.tapWhenReady()
        let name = app.textFields.firstMatch
        name.tapWhenReady()
        name.typeText("Grocery store")
        app.buttons["Choose on map"].tapWhenReady()
        app.buttons["Use current location"].tapWhenReady()
        pause(1)
        app.buttons["Use this location"].tapWhenReady()
        pause(1)
        app.buttons["Save"].tapWhenReady()

        app.buttons["Allow “Always”"].tapWhenReady()
        allowSystemAlert()
        pause(2)

        app.staticTexts["Grocery store"].firstMatch.tapWhenReady()
        app.buttons["Add a to-do"].firstMatch.tapWhenReady()
        let title = app.textFields.firstMatch
        title.tapWhenReady()
        title.typeText("Buy milk")
        app.buttons["Save"].tapWhenReady()
        pause(2)

        app.tabBars.buttons.element(boundBy: 3).tapWhenReady()
        XCTAssertTrue(app.staticTexts["Always"].waitForExistence(timeout: 5))
        pause(3)
    }

    // "Allow Once / Allow While Using App / Don't Allow", "Don't Allow / Allow", and
    // "Keep Only While Using / Change to Always Allow" all allow at index 1. On a freshly erased
    // simulator they can take more than 10 seconds to appear.
    @MainActor
    private func allowSystemAlert() {
        let alert = springboard.alerts.firstMatch
        XCTAssertTrue(alert.waitForExistence(timeout: 60))
        pause(1)
        alert.buttons.element(boundBy: 1).tap()
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
