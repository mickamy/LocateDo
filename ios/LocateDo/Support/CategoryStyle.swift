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

    private static let symbols = [
        "cart": "cart",
        "bag": "bag",
        "fork_knife": "fork.knife",
        "cup": "cup.and.saucer",
        "cross": "cross.case",
        "dumbbell": "dumbbell",
        "book": "book",
        "briefcase": "briefcase",
        "building": "building.columns",
        "house": "house",
        "car": "car",
        "fuel": "fuelpump",
        "gift": "gift",
        "pawprint": "pawprint",
        "leaf": "leaf",
        "mappin": "mappin"
    ]

    private static let tints: [String: Color] = [
        "blue": .blue,
        "green": .green,
        "orange": .orange,
        "red": .red,
        "pink": .pink,
        "purple": .purple,
        "teal": .teal,
        "yellow": .yellow,
        "brown": .brown,
        "gray": .gray
    ]

    static func systemImage(forIcon icon: String?) -> String {
        guard let icon else {
            return "tag.slash"
        }
        return symbols[icon] ?? "mappin"
    }

    static func tint(forColor color: String?) -> Color {
        guard let color else {
            return .gray
        }
        return tints[color] ?? .gray
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
