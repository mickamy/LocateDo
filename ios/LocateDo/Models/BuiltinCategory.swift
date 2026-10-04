import Foundation

nonisolated enum BuiltinCategory: String, Codable, CaseIterable, Identifiable {
    case shopping
    case work
    case life
    case other

    var id: Self { self }

    var title: LocalizedStringResource {
        switch self {
        case .shopping: .categoryShopping
        case .work: .categoryWork
        case .life: .categoryLife
        case .other: .categoryOther
        }
    }

    var icon: String {
        switch self {
        case .shopping: "cart"
        case .work: "briefcase"
        case .life: "house"
        case .other: "mappin"
        }
    }

    var color: String {
        switch self {
        case .shopping: "green"
        case .work: "blue"
        case .life: "orange"
        case .other: "gray"
        }
    }
}
