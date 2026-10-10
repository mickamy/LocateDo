import CoreLocation
import XCTest

// Drives the App Review walkthrough, in English, while `fastlane review_video` records the screen. The
// lane grants "Always" before the run and keeps only what lies between the REVIEW_VIDEO_START and
// REVIEW_VIDEO_END lines printed here. Arrival itself is explained in the video, since region
// monitoring does not run in the simulator.
final class ReviewVideoTests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
    }

    @MainActor
    func testWalkthrough() throws {
        XCUIDevice.shared.location = XCUILocation(location: CLLocation(latitude: 37.77927, longitude: -122.41924))
        let app = XCUIApplication()
        app.launchArguments += ["-completedOnboarding", "YES", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        allowNotifications(app)
        dismissKeyboardTip(app)
        XCTAssertTrue(app.buttons["Add a place"].firstMatch.waitForExistence(timeout: 10))
        print("REVIEW_VIDEO_START \(Date().timeIntervalSince1970)")
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
        pause(2)

        app.staticTexts["Grocery store"].firstMatch.tapWhenReady()
        app.buttons["Add a to-do"].firstMatch.tapWhenReady()
        let title = app.textFields.firstMatch
        title.tapWhenReady()
        title.typeText("Buy milk")
        app.buttons["Save"].tapWhenReady()
        pause(2)

        app.navigationBars.buttons.element(boundBy: 0).tapWhenReady()
        app.buttons["home.settings"].tapWhenReady()
        XCTAssertTrue(app.staticTexts["Always"].waitForExistence(timeout: 5))
        pause(3)
        print("REVIEW_VIDEO_END \(Date().timeIntervalSince1970)")
    }

    // Before the part that is kept, so the walkthrough shows notifications as allowed. The alert can take
    // more than 10 seconds to appear on a freshly erased simulator.
    @MainActor
    private func allowNotifications(_ app: XCUIApplication) {
        app.buttons["home.settings"].tapWhenReady()
        app.buttons["settings.allowNotifications"].tapWhenReady()
        let alert = XCUIApplication(bundleIdentifier: "com.apple.springboard").alerts.firstMatch
        XCTAssertTrue(alert.waitForExistence(timeout: 60))
        alert.buttons["Allow"].tap()
        XCTAssertTrue(app.staticTexts["Allowed"].waitForExistence(timeout: 10))
        app.buttons["settings.done"].tapWhenReady()
    }

    // The first keyboard on an erased simulator shows a swipe-typing tip; it is dismissed here, off camera.
    @MainActor
    private func dismissKeyboardTip(_ app: XCUIApplication) {
        app.buttons["Add a place"].firstMatch.tapWhenReady()
        app.textFields.firstMatch.tapWhenReady()
        let tip = app.buttons["Continue"]
        XCTAssertTrue(tip.waitForExistence(timeout: 10))
        tip.tap()
        app.buttons["Cancel"].tapWhenReady()
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
