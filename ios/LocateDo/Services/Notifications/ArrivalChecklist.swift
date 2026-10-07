import Foundation

// Shared with the notification content extension, which reads the items and names the check-off action.
nonisolated struct ArrivalChecklist: Equatable {
    struct Item: Equatable, Identifiable {
        let id: UUID
        let title: String
    }

    static let category = "arrival"
    static let itemsKey = "todos"
    private static let actionPrefix = "complete:"

    let items: [Item]

    init(items: [Item]) {
        self.items = items
    }

    init?(userInfo: [AnyHashable: Any]) {
        guard let entries = userInfo[Self.itemsKey] as? [[String: String]] else {
            return nil
        }
        var items: [Item] = []
        for entry in entries {
            guard let rawID = entry["id"], let id = UUID(uuidString: rawID), let title = entry["title"] else {
                continue
            }
            items.append(Item(id: id, title: title))
        }
        guard !items.isEmpty else {
            return nil
        }
        self.items = items
    }

    var encodedItems: [[String: String]] {
        items.map { ["id": $0.id.uuidString, "title": $0.title] }
    }

    static func actionIdentifier(checking ids: [UUID]) -> String {
        actionPrefix + ids.map(\.uuidString).joined(separator: ",")
    }

    static func checkedIDs(inAction identifier: String) -> [UUID]? {
        guard identifier.hasPrefix(actionPrefix) else {
            return nil
        }
        let ids = identifier.dropFirst(actionPrefix.count)
            .split(separator: ",")
            .compactMap { UUID(uuidString: String($0)) }
        guard !ids.isEmpty else {
            return nil
        }
        return ids
    }
}
