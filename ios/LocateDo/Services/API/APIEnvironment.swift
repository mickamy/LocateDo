import Foundation

nonisolated struct APIEnvironment: Sendable {
    static let infoKey = "LocateDoAPIBaseURL"
    static let overrideArgument = "-apiBaseURL"

    let baseURL: URL

    init(baseURL: URL) {
        self.baseURL = baseURL
    }

    init?(infoDictionary: [String: Any], arguments: [String]) {
        #if DEBUG
        if let index = arguments.firstIndex(of: Self.overrideArgument),
           index + 1 < arguments.count,
           let url = URL(string: arguments[index + 1]) {
            self.init(baseURL: url)
            return
        }
        #endif
        guard let raw = infoDictionary[Self.infoKey] as? String, let url = URL(string: raw) else {
            return nil
        }
        self.init(baseURL: url)
    }

    static var current: APIEnvironment {
        guard let environment = APIEnvironment(
            infoDictionary: Bundle.main.infoDictionary ?? [:],
            arguments: ProcessInfo.processInfo.arguments
        ) else {
            fatalError("\(infoKey) is missing from Info.plist")
        }
        return environment
    }
}
