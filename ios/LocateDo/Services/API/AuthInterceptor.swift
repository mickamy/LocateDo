import Connect

nonisolated final class AuthInterceptor: UnaryInterceptor, Sendable {
    private let tokens: AccessTokenStore

    init(tokens: AccessTokenStore) {
        self.tokens = tokens
    }

    @Sendable
    func handleUnaryRequest<Message: ProtobufMessage>(
        _ request: HTTPRequest<Message>,
        proceed: @escaping @Sendable (Result<HTTPRequest<Message>, ConnectError>) -> Void
    ) {
        guard let token = tokens.current else {
            proceed(.success(request))
            return
        }
        var headers = request.headers
        headers["Authorization"] = ["Bearer \(token)"]
        proceed(.success(HTTPRequest(
            url: request.url,
            headers: headers,
            message: request.message,
            method: request.method,
            trailers: request.trailers,
            idempotencyLevel: request.idempotencyLevel
        )))
    }
}
