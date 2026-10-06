import Connect
import Foundation
import SwiftProtobuf
import Synchronization
import Testing

@testable import LocateDo

struct AuthenticatorTests {
    @Test func loadsTheStoredSessionAndPublishesItsToken() {
        let session = Self.session(expiresIn: 3_600)
        let store = InMemorySessionStore(session)
        let tokens = AccessTokenStore()
        let authenticator = Authenticator(store: store, account: FakeAccountService(), tokens: tokens)

        #expect(authenticator.session == session)
        #expect(authenticator.isSignedIn)
        #expect(tokens.current == "access-1")
    }

    @Test func skipsRefreshWhileTheTokenIsFresh() async throws {
        let account = FakeAccountService()
        let authenticator = Authenticator(
            store: InMemorySessionStore(Self.session(expiresIn: 3_600)),
            account: account,
            tokens: AccessTokenStore()
        )

        try await authenticator.refreshIfNeeded()
        #expect(account.refreshCalls == 0)
    }

    @Test func refreshesAnExpiringTokenAndStoresTheRotatedSession() async throws {
        let account = FakeAccountService()
        account.enqueue(.success(Self.response(accessToken: "access-2", refreshToken: "refresh-2")))
        let store = InMemorySessionStore(Self.session(expiresIn: 10))
        let tokens = AccessTokenStore()
        let authenticator = Authenticator(store: store, account: account, tokens: tokens)

        try await authenticator.refreshIfNeeded()

        #expect(account.refreshCalls == 1)
        #expect(account.lastRefreshToken == "refresh-1")
        #expect(authenticator.session?.accessToken == "access-2")
        #expect(authenticator.session?.refreshToken == "refresh-2")
        #expect(store.current?.refreshToken == "refresh-2")
        #expect(tokens.current == "access-2")
    }

    @Test func retriesOnceAfterUnauthenticated() async throws {
        let account = FakeAccountService()
        account.enqueue(.success(Self.response(accessToken: "access-2", refreshToken: "refresh-2")))
        let authenticator = Authenticator(
            store: InMemorySessionStore(Self.session(expiresIn: 3_600)),
            account: account,
            tokens: AccessTokenStore()
        )
        let attempts = Mutex(0)

        let output = try await authenticator.authorized { () async -> ResponseMessage<Google_Protobuf_Empty> in
            let attempt = attempts.withLock { count in
                count += 1
                return count
            }
            if attempt == 1 {
                return ResponseMessage(result: .failure(ConnectError(code: .unauthenticated, message: nil)))
            }
            return ResponseMessage(result: .success(Google_Protobuf_Empty()))
        }

        #expect(output == Google_Protobuf_Empty())
        #expect(attempts.withLock { $0 } == 2)
        #expect(account.refreshCalls == 1)
    }

    @Test func doesNotRetryOtherErrors() async {
        let account = FakeAccountService()
        let authenticator = Authenticator(
            store: InMemorySessionStore(Self.session(expiresIn: 3_600)),
            account: account,
            tokens: AccessTokenStore()
        )

        await #expect(throws: ConnectError.self) {
            try await authenticator.authorized { () async -> ResponseMessage<Google_Protobuf_Empty> in
                ResponseMessage(result: .failure(ConnectError(code: .failedPrecondition, message: nil)))
            }
        }
        #expect(account.refreshCalls == 0)
    }

    @Test func signsOutWhenTheRefreshTokenIsRejected() async {
        let account = FakeAccountService()
        account.enqueue(.failure(ConnectError(code: .unauthenticated, message: nil)))
        let store = InMemorySessionStore(Self.session(expiresIn: 10))
        let tokens = AccessTokenStore()
        let authenticator = Authenticator(store: store, account: account, tokens: tokens)

        await #expect(throws: ConnectError.self) {
            try await authenticator.refreshIfNeeded()
        }
        #expect(authenticator.session == nil)
        #expect(store.current == nil)
        #expect(tokens.current == nil)
    }

    @Test func onlyARejectedRefreshEndsTheSession() async throws {
        let account = FakeAccountService()
        account.enqueue(.failure(ConnectError(code: .unavailable, message: nil)))
        account.enqueue(.failure(ConnectError(code: .unauthenticated, message: nil)))
        let authenticator = Authenticator(
            store: InMemorySessionStore(Self.session(expiresIn: 10)),
            account: account,
            tokens: AccessTokenStore()
        )
        let ended = ResetCounter()
        authenticator.onSessionEnded = { ended.increment() }

        await #expect(throws: ConnectError.self) {
            try await authenticator.refreshIfNeeded()
        }
        #expect(ended.value == 0)
        #expect(authenticator.isSignedIn)

        await #expect(throws: ConnectError.self) {
            try await authenticator.refreshIfNeeded()
        }
        #expect(ended.value == 1)

        try authenticator.signIn(Self.session(expiresIn: 3_600))
        try authenticator.signOut()
        #expect(ended.value == 1)
    }

    @Test func concurrentRefreshesShareOneRequest() async throws {
        let account = FakeAccountService()
        account.enqueue(.success(Self.response(accessToken: "access-2", refreshToken: "refresh-2")))
        account.delayRefreshes(by: .milliseconds(200))
        let authenticator = Authenticator(
            store: InMemorySessionStore(Self.session(expiresIn: 10)),
            account: account,
            tokens: AccessTokenStore()
        )

        async let first: Void = authenticator.refresh()
        async let second: Void = authenticator.refresh()
        _ = try await (first, second)

        #expect(account.refreshCalls == 1)
        #expect(authenticator.session?.refreshToken == "refresh-2")
    }

    private static func session(expiresIn seconds: TimeInterval) -> Session {
        Session(
            userID: UUID(uuidString: "0199bd00-0000-7000-8000-000000000001")!,
            accessToken: "access-1",
            accessTokenExpiresAt: Date(timeIntervalSinceNow: seconds),
            refreshToken: "refresh-1"
        )
    }

    private static func response(
        accessToken: String,
        refreshToken: String
    ) -> Locatedo_Account_V1_RefreshTokenResponse {
        var response = Locatedo_Account_V1_RefreshTokenResponse()
        response.session.userID = "0199bd00-0000-7000-8000-000000000001"
        response.session.accessToken = accessToken
        response.session.accessTokenExpiresAt = Google_Protobuf_Timestamp(date: Date(timeIntervalSinceNow: 3_600))
        response.session.refreshToken = refreshToken
        return response
    }
}

nonisolated final class InMemorySessionStore: SessionStoring {
    private let storage: Mutex<Session?>

    init(_ session: Session? = nil) {
        storage = Mutex(session)
    }

    var current: Session? {
        storage.withLock { $0 }
    }

    func load() throws -> Session? {
        current
    }

    func save(_ session: Session) throws {
        storage.withLock { $0 = session }
    }

    func clear() throws {
        storage.withLock { $0 = nil }
    }
}

nonisolated final class FakeAccountService: Locatedo_Account_V1_AccountServiceClientInterface {
    private struct State {
        var queue: [Result<Locatedo_Account_V1_RefreshTokenResponse, ConnectError>] = []
        var refreshCalls = 0
        var lastRefreshToken: String?
        var signInResult: Result<Locatedo_Account_V1_SignInWithAppleResponse, ConnectError> =
            .failure(ConnectError(code: .unimplemented, message: nil))
        var lastSignIn: Locatedo_Account_V1_SignInWithAppleRequest?
        var deleteCalls = 0
        var syncEntitlementCalls = 0
        var refreshDelay: Duration?
        var signOutTokens: [String] = []
        var signOutPushTokens: [String] = []
    }

    private let state = Mutex(State())

    var refreshCalls: Int {
        state.withLock { $0.refreshCalls }
    }

    var lastRefreshToken: String? {
        state.withLock { $0.lastRefreshToken }
    }

    var lastSignIn: Locatedo_Account_V1_SignInWithAppleRequest? {
        state.withLock { $0.lastSignIn }
    }

    var deleteCalls: Int {
        state.withLock { $0.deleteCalls }
    }

    var syncEntitlementCalls: Int {
        state.withLock { $0.syncEntitlementCalls }
    }

    func delayRefreshes(by delay: Duration) {
        state.withLock { $0.refreshDelay = delay }
    }

    var signOutTokens: [String] {
        state.withLock { $0.signOutTokens }
    }

    var signOutPushTokens: [String] {
        state.withLock { $0.signOutPushTokens }
    }

    func enqueue(_ result: Result<Locatedo_Account_V1_RefreshTokenResponse, ConnectError>) {
        state.withLock { $0.queue.append(result) }
    }

    func respondToSignIn(with result: Result<Locatedo_Account_V1_SignInWithAppleResponse, ConnectError>) {
        state.withLock { $0.signInResult = result }
    }

    func refreshToken(
        request: Locatedo_Account_V1_RefreshTokenRequest,
        headers: Connect.Headers
    ) async -> ResponseMessage<Locatedo_Account_V1_RefreshTokenResponse> {
        if let delay = state.withLock({ $0.refreshDelay }) {
            try? await Task.sleep(for: delay)
        }
        let result = state.withLock { state in
            state.refreshCalls += 1
            state.lastRefreshToken = request.refreshToken
            if state.queue.isEmpty {
                return Result<Locatedo_Account_V1_RefreshTokenResponse, ConnectError>
                    .failure(ConnectError(code: .unknown, message: "no response queued"))
            }
            return state.queue.removeFirst()
        }
        return ResponseMessage(result: result)
    }

    func signInWithApple(
        request: Locatedo_Account_V1_SignInWithAppleRequest,
        headers: Connect.Headers
    ) async -> ResponseMessage<Locatedo_Account_V1_SignInWithAppleResponse> {
        let result = state.withLock { state in
            state.lastSignIn = request
            return state.signInResult
        }
        return ResponseMessage(result: result)
    }

    func signInWithGoogle(
        request: Locatedo_Account_V1_SignInWithGoogleRequest,
        headers: Connect.Headers
    ) async -> ResponseMessage<Locatedo_Account_V1_SignInWithGoogleResponse> {
        ResponseMessage(result: .failure(ConnectError(code: .unimplemented, message: nil)))
    }

    func deleteAccount(
        request: Locatedo_Account_V1_DeleteAccountRequest,
        headers: Connect.Headers
    ) async -> ResponseMessage<Locatedo_Account_V1_DeleteAccountResponse> {
        state.withLock { $0.deleteCalls += 1 }
        return ResponseMessage(result: .success(Locatedo_Account_V1_DeleteAccountResponse()))
    }

    func syncEntitlement(
        request: Locatedo_Account_V1_SyncEntitlementRequest,
        headers: Connect.Headers
    ) async -> ResponseMessage<Locatedo_Account_V1_SyncEntitlementResponse> {
        state.withLock { $0.syncEntitlementCalls += 1 }
        return ResponseMessage(result: .success(Locatedo_Account_V1_SyncEntitlementResponse()))
    }

    func signOut(
        request: Locatedo_Account_V1_SignOutRequest,
        headers: Connect.Headers
    ) async -> ResponseMessage<Locatedo_Account_V1_SignOutResponse> {
        state.withLock { state in
            state.signOutTokens.append(request.refreshToken)
            if request.hasDevice {
                state.signOutPushTokens.append(request.device.pushToken)
            }
        }
        return ResponseMessage(result: .success(Locatedo_Account_V1_SignOutResponse()))
    }
}
