import Foundation

// Shared with the notification content extension, which shows the items as a checklist.
nonisolated struct ArrivalChecklist: Equatable {
    struct Item: Equatable, Identifiable {
        let id: UUID
        let title: String
    }

    static let category = "arrival"
    static let checkOffAction = "checkOff"
    static let itemsKey = "todos"
    static let categoryIconKey = "categoryIcon"
    static let categoryColorKey = "categoryColor"

    let items: [Item]
    let categoryIcon: String?
    let categoryColor: String?

    init(items: [Item], categoryIcon: String? = nil, categoryColor: String? = nil) {
        self.items = items
        self.categoryIcon = categoryIcon
        self.categoryColor = categoryColor
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
        categoryIcon = userInfo[Self.categoryIconKey] as? String
        categoryColor = userInfo[Self.categoryColorKey] as? String
    }

    var userInfo: [String: Any] {
        var info: [String: Any] = [
            Self.itemsKey: items.map { ["id": $0.id.uuidString, "title": $0.title] }
        ]
        if let categoryIcon {
            info[Self.categoryIconKey] = categoryIcon
        }
        if let categoryColor {
            info[Self.categoryColorKey] = categoryColor
        }
        return info
    }
}
