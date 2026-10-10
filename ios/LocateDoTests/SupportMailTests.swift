import Foundation
import Testing

@testable import LocateDo

struct SupportMailTests {
    private let diagnostics = SupportDiagnostics(
        appVersion: "1.0.1 (10)",
        osVersion: "26.1",
        deviceModel: "iPhone17,1",
        language: "ja",
        timeZone: "Asia/Tokyo",
        supportID: "1A2B3C",
        sharesUsageData: true,
        userID: UUID(uuidString: "0198F2A4-1C3B-7D2E-9F00-0123456789AB"),
        plan: .trial,
        locationAuth: .always,
        preciseLocation: true,
        notificationAuth: .authorized,
        backgroundRefresh: .on,
        lowPowerMode: false
    )

    @Test func listsEveryDetail() {
        #expect(diagnostics.text == """
        App: StopBy 1.0.1 (10)
        OS: iOS 26.1 (iPhone17,1)
        Language: ja / Time zone: Asia/Tokyo
        Support ID: 1A2B3C
        User ID: 0198f2a4-1c3b-7d2e-9f00-0123456789ab
        Plan: trial
        Location: always, precise
        Notifications: authorized
        Background App Refresh: on
        Low Power Mode: off
        """)
    }

    @Test func leavesOutMissingIDs() {
        var signedOut = diagnostics
        signedOut.supportID = nil
        signedOut.userID = nil
        signedOut.preciseLocation = false
        signedOut.lowPowerMode = true

        #expect(!signedOut.text.contains("Support ID"))
        #expect(!signedOut.text.contains("User ID"))
        #expect(signedOut.text.contains("Location: always, approximate"))
        #expect(signedOut.text.contains("Low Power Mode: on"))
        #expect(!signedOut.text.contains("Usage data"))
    }

    @Test func saysUsageDataIsOffInsteadOfTheSupportID() {
        var declined = diagnostics
        declined.supportID = nil
        declined.sharesUsageData = false

        #expect(!declined.text.contains("Support ID"))
        #expect(declined.text.contains("Usage data: off"))
    }

    @Test func encodesSubjectAndBody() throws {
        let url = try #require(SupportMail.url(subject: "StopBy Support", body: "a&b=c+d\n■ 日時：?"))

        let expected = "mailto:support@locatedo.com?subject=StopBy%20Support"
            + "&body=a%26b%3Dc%2Bd%0A%E2%96%A0%20%E6%97%A5%E6%99%82%EF%BC%9A%3F"
        #expect(url.absoluteString == expected)
    }
}
