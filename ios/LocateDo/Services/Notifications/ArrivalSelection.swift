import Foundation

// The notification extension keeps what is checked here, in the app group, and the app takes it when the
// check-off action wakes it. Changing the action itself while the notification is open does not show reliably.
nonisolated struct ArrivalSelection {
    private static let keyPrefix = "arrivalSelection."

    let defaults: UserDefaults

    static func shared() -> ArrivalSelection? {
        guard let group = Bundle.main.object(forInfoDictionaryKey: "LocateDoAppGroup") as? String,
              let defaults = UserDefaults(suiteName: group) else {
            return nil
        }
        return ArrivalSelection(defaults: defaults)
    }

    func save(_ ids: [UUID], for requestID: String) {
        let key = Self.keyPrefix + requestID
        if ids.isEmpty {
            defaults.removeObject(forKey: key)
            return
        }
        defaults.set(ids.map(\.uuidString), forKey: key)
    }

    func take(for requestID: String) -> [UUID] {
        let key = Self.keyPrefix + requestID
        let raw = defaults.stringArray(forKey: key) ?? []
        defaults.removeObject(forKey: key)
        return raw.compactMap { UUID(uuidString: $0) }
    }
}
