import Foundation
import Testing

@testable import LocateDo

struct PlacePresenceTests {
    private static let now = Date(timeIntervalSince1970: 1_800_000_000)

    @Test func enteringRecordsTheTimeAndArrives() {
        let outcome = PlacePresence.next(.inside, enteredAt: nil, isFirstReport: false, now: Self.now)
        #expect(outcome == PlacePresence.Outcome(enteredAt: Self.now, event: .arrival))
    }

    @Test func enteringAgainKeepsTheFirstTime() {
        let entered = Self.now.addingTimeInterval(-60)
        let outcome = PlacePresence.next(.inside, enteredAt: entered, isFirstReport: false, now: Self.now)
        #expect(outcome == PlacePresence.Outcome(enteredAt: entered, event: .arrival))
    }

    @Test func leavingAfterFiveMinutesDeparts() {
        let entered = Self.now.addingTimeInterval(-PlacePresence.minimumStay)
        let outcome = PlacePresence.next(.outside, enteredAt: entered, isFirstReport: false, now: Self.now)
        #expect(outcome == PlacePresence.Outcome(enteredAt: nil, event: .departure(stay: PlacePresence.minimumStay)))
    }

    @Test func leavingSoonerIsAShortStay() {
        let entered = Self.now.addingTimeInterval(-120)
        let outcome = PlacePresence.next(.outside, enteredAt: entered, isFirstReport: false, now: Self.now)
        #expect(outcome == PlacePresence.Outcome(enteredAt: nil, event: .shortStay(stay: 120)))
    }

    @Test func leavingWithoutAnEntryTimeDoesNothing() {
        let outcome = PlacePresence.next(.outside, enteredAt: nil, isFirstReport: false, now: Self.now)
        #expect(outcome == PlacePresence.Outcome())
    }

    @Test func theFirstReportInsideArrivesAndStartsCounting() {
        let outcome = PlacePresence.next(.inside, enteredAt: nil, isFirstReport: true, now: Self.now)
        #expect(outcome == PlacePresence.Outcome(enteredAt: Self.now, event: .arrival))
    }

    @Test func theFirstReportInsideKeepsARecordedTime() {
        let entered = Self.now.addingTimeInterval(-3_600)
        let outcome = PlacePresence.next(.inside, enteredAt: entered, isFirstReport: true, now: Self.now)
        #expect(outcome == PlacePresence.Outcome(enteredAt: entered, event: .arrival))
    }

    @Test func theFirstReportOutsideClearsTheTimeWithoutDeparting() {
        let entered = Self.now.addingTimeInterval(-3_600)
        let outcome = PlacePresence.next(.outside, enteredAt: entered, isFirstReport: true, now: Self.now)
        #expect(outcome == PlacePresence.Outcome())
    }
}
