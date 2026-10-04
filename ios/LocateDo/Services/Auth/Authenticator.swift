import Connect
import Foundation
import Observation
import OSLog

enum AuthError: Error {
    case signedOut
    case invalidSession
}

@Observable
final class Authenticator {
    static let refreshLeeway: TimeInterval = 60

    private(set) var session: Session?

    private let store: any SessionStoring
    private let account: any Locatedo_Account_V1_AccountServiceClientInterface
    private let tokens: AccessTokenStore
    private let logger = Logger(subsystem: "com.locatedo.LocateDo", category: "auth")
    private var inFlightRefresh: Task<Void, any Error>?

    init(
        store: any SessionStoring,
        account: any Locatedo_Account_V1_AccountServiceClientInterface,
        tokens: AccessTokenStore
    ) {
        self.store = store
        self.account = account
        self.tokens = tokens
        do {
            session = try store.load()
        } catch {
            logger.error("Could not load the session: \(error, privacy: .public)")
        }
        tokens.update(session?.accessToken)
    }

    var isSignedIn: Bool {
        session != nil
    }

    func signIn(_ session: Session) throws {
        try store.save(session)
        self.session = session
        tokens.update(session.accessToken)
    }

    func signOut() throws {
        try store.clear()
        session = nil
        tokens.update(nil)
    }

    func refreshIfNeeded(now: Date = .now) async throws {
        guard let session, session.isExpiring(at: now, within: Self.refreshLeeway) else {
            return
        }
        try await refresh(using: session)
    }

    func refresh() async throws {
        guard let session else {
            throw AuthError.signedOut
        }
        try await refresh(using: session)
    }

    func authorized<Output: ProtobufMessage>(
        _ call: @Sendable () async -> ResponseMessage<Output>
    ) async throws -> Output {
        try await refreshIfNeeded()
        switch await call().result {
        case .success(let output):
            return output
        case .failure(let error) where error.code == .unauthenticated:
            try await refresh()
            return try await call().result.get()
        case .failure(let error):
            throw error
        }
    }

    private func refresh(using session: Session) async throws {
        if let inFlightRefresh {
            try await inFlightRefresh.value
            return
        }
        let task = Task {
            defer { inFlightRefresh = nil }
            try await performRefresh(using: session)
        }
        inFlightRefresh = task
        try await task.value
    }

    private func performRefresh(using session: Session) async throws {
        var request = Locatedo_Account_V1_RefreshTokenRequest()
        request.refreshToken = session.refreshToken
        let response = await account.refreshToken(request: request, headers: [:])
        switch response.result {
        case .success(let output):
            guard let refreshed = Session(output.session) else {
                throw AuthError.invalidSession
            }
            try signIn(refreshed)
        case .failure(let error):
            if error.code == .unauthenticated || error.code == .permissionDenied {
                logger.notice("Refresh rejected (\(error.code.name, privacy: .public)); signing out")
                try signOut()
            }
            throw error
        }
    }
}
