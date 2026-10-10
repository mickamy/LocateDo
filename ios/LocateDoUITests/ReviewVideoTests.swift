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

        // Adding a place starts on the map, then names it, picks its category, and writes its to-dos.
        app.buttons["Add a place"].firstMatch.tapWhenReady()
        app.buttons["Use current location"].tapWhenReady()
        pause(1)
        app.buttons["Use this location"].tapWhenReady()
        pause(1)
        app.textFields.firstMatch.replaceText(with: "Grocery store")
        app.buttons["Next"].tapWhenReady()
        app.staticTexts["Shopping"].firstMatch.tapWhenReady()
        pause(1)
        app.buttons["Next"].tapWhenReady()
        app.textFields["placeEditor.todoDraft"].tapWhenReady()
        app.textFields["placeEditor.todoDraft"].typeText("Milk")
        pause(1)
        app.buttons["Save"].tapWhenReady()
        pause(2)

        app.staticTexts["Grocery store"].firstMatch.tapWhenReady()
        XCTAssertTrue(app.todoRow("Milk").waitForExistence(timeout: 5))
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

    // The first keyboard on an erased simulator shows a swipe-typing tip; it is dismissed here, off camera, from the
    // map's search field. Cancel closes the search, then the sheet.
    @MainActor
    private func dismissKeyboardTip(_ app: XCUIApplication) {
        app.buttons["Add a place"].firstMatch.tapWhenReady()
        app.searchFields.firstMatch.tapWhenReady()
        let tip = app.buttons["Continue"]
        XCTAssertTrue(tip.waitForExistence(timeout: 10))
        tip.tap()
        while app.buttons["Cancel"].firstMatch.waitForExistence(timeout: 2) {
            app.buttons["Cancel"].firstMatch.tap()
        }
        XCTAssertTrue(app.buttons["Add a place"].firstMatch.waitForExistence(timeout: 5))
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

    // The name comes filled in from the address of the pick, so it is cleared before typing.
    @MainActor
    func replaceText(with text: String) {
        tapWhenReady()
        let current = value as? String ?? ""
        typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: current.count))
        typeText(text)
    }
}
