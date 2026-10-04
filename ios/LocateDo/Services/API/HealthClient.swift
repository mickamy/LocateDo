import Foundation

nonisolated struct HealthClient: Sendable {
    let environment: APIEnvironment

    func statusCode() async throws -> Int {
        let (_, response) = try await URLSession.shared.data(from: environment.baseURL.appending(path: "healthz"))
        guard let http = response as? HTTPURLResponse else {
            throw URLError(.badServerResponse)
        }
        return http.statusCode
    }
}
