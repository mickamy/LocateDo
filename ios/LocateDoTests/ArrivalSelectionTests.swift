import Foundation
import Testing

@testable import LocateDo

struct ArrivalSelectionTests {
    @Test func theAppTakesWhatTheChecklistKept() throws {
        let selection = ArrivalSelection(defaults: try makeDefaults())
        let ids = [UUID(), UUID()]

        selection.save(ids, for: "arrival-a")

        #expect(selection.take(for: "arrival-a") == ids)
        #expect(selection.take(for: "arrival-a") == [])
    }

    @Test func eachNotificationKeepsItsOwnSelection() throws {
        let selection = ArrivalSelection(defaults: try makeDefaults())
        let milk = UUID()
        let eggs = UUID()

        selection.save([milk], for: "arrival-a")
        selection.save([eggs], for: "arrival-b")

        #expect(selection.take(for: "arrival-a") == [milk])
        #expect(selection.take(for: "arrival-b") == [eggs])
    }

    @Test func uncheckingEverythingLeavesNothing() throws {
        let selection = ArrivalSelection(defaults: try makeDefaults())
        selection.save([UUID()], for: "arrival-a")

        selection.save([], for: "arrival-a")

        #expect(selection.take(for: "arrival-a") == [])
    }

    private func makeDefaults() throws -> UserDefaults {
        let suite = "ArrivalSelectionTests.\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suite))
        defaults.removePersistentDomain(forName: suite)
        return defaults
    }
}
