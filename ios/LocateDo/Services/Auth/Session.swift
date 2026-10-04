import Foundation
import SwiftProtobuf

nonisolated struct Session: Codable, Equatable, Sendable {
    var userID: UUID
    var accessToken: String
    var accessTokenExpiresAt: Date
    var refreshToken: String

    func isExpiring(at now: Date, within leeway: TimeInterval) -> Bool {
        accessTokenExpiresAt.timeIntervalSince(now) <= leeway
    }
}

extension Session {
    init?(_ proto: Locatedo_Account_V1_Session) {
        guard let userID = UUID(uuidString: proto.userID) else {
            return nil
        }
        self.init(
            userID: userID,
            accessToken: proto.accessToken,
            accessTokenExpiresAt: proto.accessTokenExpiresAt.date,
            refreshToken: proto.refreshToken
        )
    }
}
