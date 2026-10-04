import Synchronization

nonisolated final class AccessTokenStore: Sendable {
    private let token = Mutex<String?>(nil)

    var current: String? {
        token.withLock { $0 }
    }

    func update(_ newValue: String?) {
        token.withLock { $0 = newValue }
    }
}
