import Foundation

// Holds what is logged before the person's answer is known, in memory only, so nothing leaves the device without it.
nonisolated struct AnalyticsQueue {
    enum Entry: Sendable {
        case event(name: String, parameters: [String: any AnalyticsValue])
        case userProperty(name: String, value: String?)
    }

    enum Mode {
        case holding
        case sending
        case dropping
    }

    static let limit = 100

    private(set) var mode = Mode.holding
    private(set) var entries: [Entry] = []

    // Whether the entry goes out now.
    mutating func submit(_ entry: Entry) -> Bool {
        switch mode {
        case .sending:
            return true
        case .holding:
            if entries.count < Self.limit {
                entries.append(entry)
            }
            return false
        case .dropping:
            return false
        }
    }

    // nil keeps holding; true hands back what was held so it can be sent; false drops it.
    mutating func decide(_ decision: Bool?) -> [Entry] {
        guard let decision else {
            mode = .holding
            return []
        }
        let held = entries
        entries = []
        if decision {
            mode = .sending
            return held
        }
        mode = .dropping
        return []
    }
}
