import Foundation

// Shared with the Watch app, which sends this to iPhone to complete to-dos.
nonisolated struct WatchCheckOff: Equatable {
    enum Source: String {
        case app
        case notification
    }

    static let todoIDsKey = "checkOff"
    static let sourceKey = "source"

    let todoIDs: [UUID]
    let source: Source

    init(todoIDs: [UUID], source: Source) {
        self.todoIDs = todoIDs
        self.source = source
    }

    init?(message: [String: Any]) {
        guard let rawIDs = message[Self.todoIDsKey] as? [String],
              let rawSource = message[Self.sourceKey] as? String,
              let source = Source(rawValue: rawSource) else {
            return nil
        }
        let todoIDs = rawIDs.compactMap { UUID(uuidString: $0) }
        guard !todoIDs.isEmpty else {
            return nil
        }
        self.todoIDs = todoIDs
        self.source = source
    }

    var message: [String: Any] {
        [
            Self.todoIDsKey: todoIDs.map(\.uuidString),
            Self.sourceKey: source.rawValue
        ]
    }
}
