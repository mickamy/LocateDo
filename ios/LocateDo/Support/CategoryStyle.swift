import SwiftUI

nonisolated struct CategoryStyle {
    let name: String
    let systemImage: String
    let tint: Color

    init(_ category: PlaceCategory?) {
        guard let category else {
            name = String(localized: .categoryNone)
            systemImage = Self.systemImage(forIcon: nil)
            tint = Self.tint(forColor: nil)
            return
        }
        name = category.displayName
        systemImage = Self.systemImage(forIcon: category.icon)
        tint = Self.tint(forColor: category.color)
    }

    static func systemImage(forIcon icon: String?) -> String {
        switch icon {
        case "cart": "cart"
        case "briefcase": "briefcase"
        case "house": "house"
        case nil: "tag.slash"
        default: "mappin"
        }
    }

    static func tint(forColor color: String?) -> Color {
        switch color {
        case "green": .green
        case "blue": .blue
        case "orange": .orange
        default: .gray
        }
    }
}

extension Place {
    var categoryStyle: CategoryStyle {
        CategoryStyle(category)
    }
}
