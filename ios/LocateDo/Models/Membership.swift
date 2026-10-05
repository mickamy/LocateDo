import Foundation
import SwiftData

nonisolated enum MemberRole: String, Codable {
    case owner
    case member
}

@Model
final class Membership {
    @Attribute(.unique) var userID: UUID
    var role: MemberRole
    var displayName: String
    var joinedAt: Date
    var updatedAt: Date

    init(userID: UUID, role: MemberRole, displayName: String, joinedAt: Date, updatedAt: Date) {
        self.userID = userID
        self.role = role
        self.displayName = displayName
        self.joinedAt = joinedAt
        self.updatedAt = updatedAt
    }
}

extension Membership {
    var shownName: String {
        if displayName.isEmpty {
            return String(localized: .sharingUnnamedMember)
        }
        return displayName
    }
}
