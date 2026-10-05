import Foundation

nonisolated enum InviteLink {
    private static let hosts: Set<String> = ["locatedo.com", "www.locatedo.com"]
    private static let tokenCharacters = CharacterSet(
        charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    )

    static func url(for token: String) -> URL? {
        var components = URLComponents()
        components.scheme = "https"
        components.host = "locatedo.com"
        components.path = "/i/\(token)"
        return components.url
    }

    static func token(from text: String) -> String? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if let url = URL(string: trimmed), let host = url.host() {
            guard hosts.contains(host) else {
                return nil
            }
            let parts = url.pathComponents
            guard parts.count == 3, parts[1] == "i" else {
                return nil
            }
            return validToken(parts[2])
        }
        return validToken(trimmed)
    }

    private static func validToken(_ candidate: String) -> String? {
        guard candidate.count >= 16, candidate.unicodeScalars.allSatisfy(tokenCharacters.contains) else {
            return nil
        }
        return candidate
    }
}
