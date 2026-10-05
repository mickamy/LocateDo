import Foundation

nonisolated struct AppStatusDocument: Codable, Equatable, Sendable {
    struct Localized: Codable, Equatable, Sendable {
        let ja: String?
        let en: String?

        func text(for languageCode: String?) -> String? {
            if languageCode == "ja" {
                return ja ?? en
            }
            return en ?? ja
        }
    }

    struct MinimumVersion: Codable, Equatable, Sendable {
        let ios: String?
    }

    struct Maintenance: Codable, Equatable, Sendable {
        let startsAt: Date
        let endsAt: Date
        let message: Localized?

        var key: String {
            "\(startsAt.timeIntervalSince1970)-\(endsAt.timeIntervalSince1970)"
        }
    }

    struct Notice: Codable, Equatable, Sendable {
        let id: String
        let until: Date
        let message: Localized
    }

    let minimumVersion: MinimumVersion?
    let maintenance: Maintenance?
    let notice: Notice?

    static func decode(_ data: Data) throws -> AppStatusDocument {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(AppStatusDocument.self, from: data)
    }
}

nonisolated enum MaintenancePhase: Equatable, Sendable {
    case none
    case upcoming(AppStatusDocument.Maintenance)
    case active(AppStatusDocument.Maintenance)

    init(_ maintenance: AppStatusDocument.Maintenance?, at now: Date) {
        guard let maintenance, now < maintenance.endsAt else {
            self = .none
            return
        }
        if now < maintenance.startsAt {
            self = .upcoming(maintenance)
        } else {
            self = .active(maintenance)
        }
    }
}

nonisolated enum AppVersion {
    static func isOlder(_ version: String, than minimum: String) -> Bool {
        let lhs = components(of: version)
        let rhs = components(of: minimum)
        for index in 0..<max(lhs.count, rhs.count) {
            let left = component(lhs, at: index)
            let right = component(rhs, at: index)
            if left != right {
                return left < right
            }
        }
        return false
    }

    private static func component(_ components: [Int], at index: Int) -> Int {
        guard index < components.count else {
            return 0
        }
        return components[index]
    }

    private static func components(of version: String) -> [Int] {
        version.split(separator: ".").map { Int($0) ?? 0 }
    }
}
