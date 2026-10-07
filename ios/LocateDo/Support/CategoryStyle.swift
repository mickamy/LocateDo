import SwiftUI

nonisolated struct CategoryStyle {
    let name: String
    let systemImage: String
    let tint: Color

    init(_ category: PlaceCategory?) {
        guard let category else {
            name = String(localized: .categoryNone)
            systemImage = CategoryAppearance.systemImage(forIcon: nil)
            tint = CategoryAppearance.tint(forColor: nil)
            return
        }
        name = category.displayName
        systemImage = CategoryAppearance.systemImage(forIcon: category.icon)
        tint = CategoryAppearance.tint(forColor: category.color)
    }
}

nonisolated enum CategoryPalette {
    static let icons = [
        "cart", "bag", "fork_knife", "cup", "cross", "dumbbell", "book", "briefcase",
        "building", "house", "car", "fuel", "gift", "pawprint", "leaf", "mappin"
    ]

    static let colors = ["blue", "green", "orange", "red", "pink", "purple", "teal", "yellow", "brown", "gray"]

    private static let colorNames: [String: LocalizedStringResource] = [
        "blue": .categoryColorBlue,
        "green": .categoryColorGreen,
        "orange": .categoryColorOrange,
        "red": .categoryColorRed,
        "pink": .categoryColorPink,
        "purple": .categoryColorPurple,
        "teal": .categoryColorTeal,
        "yellow": .categoryColorYellow,
        "brown": .categoryColorBrown,
        "gray": .categoryColorGray
    ]

    static func colorName(_ color: String) -> LocalizedStringResource {
        colorNames[color] ?? .categoryColorGray
    }
}

extension Place {
    var categoryStyle: CategoryStyle {
        CategoryStyle(category)
    }
}
