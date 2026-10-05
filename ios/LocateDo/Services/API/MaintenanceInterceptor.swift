import Connect

nonisolated final class MaintenanceInterceptor: UnaryInterceptor, Sendable {
    private let gate: MaintenanceGate

    init(gate: MaintenanceGate) {
        self.gate = gate
    }

    @Sendable
    func handleUnaryRequest<Message: ProtobufMessage>(
        _ request: HTTPRequest<Message>,
        proceed: @escaping @Sendable (Result<HTTPRequest<Message>, ConnectError>) -> Void
    ) {
        if gate.isClosed() {
            proceed(.failure(ConnectError(code: .unavailable, message: "the server is under maintenance")))
            return
        }
        proceed(.success(request))
    }
}
