import Foundation
import SwiftData

nonisolated enum Plan: String, Codable {
    case free
    case pro
}

@Model
final class SyncState {
    var householdID: UUID?
    var cursor: Int64
    var plan: Plan

    init(householdID: UUID? = nil, cursor: Int64 = 0, plan: Plan = .free) {
        self.householdID = householdID
        self.cursor = cursor
        self.plan = plan
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
