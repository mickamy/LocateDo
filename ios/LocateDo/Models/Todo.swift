import Foundation
import SwiftData

@Model
final class Todo {
    @Attribute(.unique) var id: UUID
    var title: String
    var place: Place?
    var assigneeID: UUID?
    var creatorID: UUID?
    var completedAt: Date?
    var completerID: UUID?
    var createdAt: Date
    var updatedAt: Date

    init(id: UUID = .v7(), title: String, place: Place, now: Date = .now) {
        self.id = id
        self.title = title
        self.place = place
        createdAt = now
        updatedAt = now
    }

    var isCompleted: Bool {
        completedAt != nil
    }

    func complete(by userID: UUID? = nil, at now: Date = .now) {
        completedAt = now
        completerID = userID
        updatedAt = now
    }

    func reopen(at now: Date = .now) {
        completedAt = nil
        completerID = nil
        updatedAt = now
    }
}

nonisolated extension [Todo] {
    var open: [Todo] {
        filter { !$0.isCompleted }.sorted { $0.createdAt < $1.createdAt }
    }

    var completedNewestFirst: [Todo] {
        filter(\.isCompleted).sorted { ($0.completedAt ?? .distantPast) > ($1.completedAt ?? .distantPast) }
    }
}
