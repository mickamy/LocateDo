import Foundation
import SwiftData

@Model
final class SyncState {
    var householdID: UUID?
    var cursor: Int64

    init(householdID: UUID? = nil, cursor: Int64 = 0) {
        self.householdID = householdID
        self.cursor = cursor
    }

    static func current(in context: ModelContext) throws -> SyncState {
        if let existing = try context.fetch(FetchDescriptor<SyncState>()).first {
            return existing
        }
        let state = SyncState()
        context.insert(state)
        return state
    }
}
