import SwiftUI

// Shared with the notification content extension, which has no SwiftData models.
nonisolated enum CategoryAppearance {
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
