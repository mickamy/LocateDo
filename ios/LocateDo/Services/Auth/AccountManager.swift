import Connect
import Foundation
import Observation
import OSLog
import SwiftData

@Observable
final class AccountManager {
    private struct PendingAdoption {
        let session: Session
        let householdID: UUID
    }

    private(set) var isWorking = false
    private(set) var isResettingLocalData = false
    private var pendingAdoption: PendingAdoption?
    private var screenClearance: CheckedContinuation<Void, Never>?
    @ObservationIgnored var pushToken: () -> String? = { nil }
    @ObservationIgnored var onLocalDataReset: () async -> Void = {}
    @ObservationIgnored var onHouseholdReady: () -> Void = {}
    @ObservationIgnored var onSessionEnded: () async -> Void = {}
    @ObservationIgnored var onSignedOut: () async -> Void = {}

    private let account: any Locatedo_Account_V1_AccountServiceClientInterface
    private let household: any Locatedo_Household_V1_HouseholdServiceClientInterface
    private let authenticator: Authenticator
    private let context: ModelContext
    private let resetsOffscreen: Bool
    private var pendingHouseholdID: UUID?
    private let logger = Logger(subsystem: "com.locatedo.LocateDo", category: "account")

    init(
        account: any Locatedo_Account_V1_AccountServiceClientInterface,
        household: any Locatedo_Household_V1_HouseholdServiceClientInterface,
        authenticator: Authenticator,
        context: ModelContext,
        resetsOffscreen: Bool = true
    ) {
        self.account = account
        self.household = household
        self.authenticator = authenticator
        self.context = context
        self.resetsOffscreen = resetsOffscreen
    }

    var isSignedIn: Bool {
        authenticator.isSignedIn
    }

    var needsReplaceConfirmation: Bool {
        pendingAdoption != nil
    }

    func signInWithApple(
        identityToken: String,
        authorizationCode: String,
        nonce: String,
        displayName: String?
    ) async throws {
        isWorking = true
        defer { isWorking = false }

        var request = Locatedo_Account_V1_SignInWithAppleRequest()
        request.identityToken = identityToken
        request.authorizationCode = authorizationCode
        request.nonce = nonce
        if let displayName, !displayName.isEmpty {
            request.displayName = displayName
        }
        let response = try await account.signInWithApple(request: request, headers: [:]).result.get()
        guard let session = Session(response.session) else {
            throw AuthError.invalidSession
        }

        if response.hasHouseholdID, let householdID = UUID(uuidString: response.householdID) {
            let adoption = PendingAdoption(session: session, householdID: householdID)
            if try hasUserData() {
                pendingAdoption = adoption
                return
            }
            try await adopt(adoption)
            return
        }
        try authenticator.signIn(session)
        try await uploadLocalDataIfNeeded()
    }

    func confirmReplacingLocalData() async throws {
        guard let adoption = pendingAdoption else {
            return
        }
        isWorking = true
        defer { isWorking = false }

        pendingAdoption = nil
        try await adopt(adoption)
    }

    func cancelReplacingLocalData() {
        pendingAdoption = nil
    }

    func uploadLocalDataIfNeeded() async throws {
        guard isSignedIn else {
            return
        }
        let state = try SyncState.current(in: context)
        guard state.householdID == nil else {
            return
        }
        let householdID = pendingHouseholdID ?? .v7()
        pendingHouseholdID = householdID
        let request = InitialUpload.request(
            householdID: householdID,
            categories: try context.fetch(FetchDescriptor<PlaceCategory>()),
            places: try context.fetch(FetchDescriptor<Place>()),
            todos: try context.fetch(FetchDescriptor<Todo>())
        )
        let client = household
        let response = try await authenticator.authorized {
            await client.createHousehold(request: request, headers: [:])
        }
        state.householdID = UUID(uuidString: response.household.id) ?? householdID
        state.cursor = response.cursor
        try context.save()
        pendingHouseholdID = nil
        onHouseholdReady()
    }

    func deleteAccount() async throws {
        isWorking = true
        defer { isWorking = false }

        let client = account
        _ = try await authenticator.authorized {
            await client.deleteAccount(request: Locatedo_Account_V1_DeleteAccountRequest(), headers: [:])
        }
        try authenticator.signOut()
        try await offscreen {
            try resetLocalData()
            await onLocalDataReset()
        }
    }

    // A purchase made before signing in sends no webhook naming the user, so the server re-reads it once linked.
    func linkPurchases(_ entitlements: Entitlements) async {
        guard let userID = authenticator.session?.userID else {
            return
        }
        guard await entitlements.logIn(userID: userID) else {
            return
        }
        let client = account
        do {
            _ = try await authenticator.authorized {
                await client.syncEntitlement(request: Locatedo_Account_V1_SyncEntitlementRequest(), headers: [:])
            }
        } catch {
            logger.notice("SyncEntitlement failed: \(error, privacy: .public)")
        }
    }

    func hasUnsyncedWrites() throws -> Bool {
        try context.fetchCount(FetchDescriptor<PendingWrite>()) > 0
    }

    func signOut() async throws {
        isWorking = true
        defer { isWorking = false }

        if let refreshToken = authenticator.session?.refreshToken {
            var request = Locatedo_Account_V1_SignOutRequest()
            request.refreshToken = refreshToken
            if let pushToken = pushToken() {
                request.device.platform = .ios
                request.device.pushToken = pushToken
            }
            if case .failure(let error) = await account.signOut(request: request, headers: [:]).result {
                logger.notice("Server sign-out failed; signing out locally: \(error, privacy: .public)")
            }
        }
        try authenticator.signOut()
        pendingAdoption = nil
        try await offscreen {
            try resetLocalData()
            await onSignedOut()
        }
    }

    func startOver() async throws {
        pendingAdoption = nil
        try await offscreen {
            try resetLocalData()
            try await uploadLocalDataIfNeeded()
        }
    }

    func endSession() async {
        pendingAdoption = nil
        await offscreen {
            do {
                try resetLocalData()
            } catch {
                logger.error("Could not clear local data after the session ended: \(error, privacy: .public)")
            }
            await onSessionEnded()
        }
    }

    private func hasUserData() throws -> Bool {
        if try context.fetchCount(FetchDescriptor<Place>()) > 0 {
            return true
        }
        return try context.fetch(FetchDescriptor<PlaceCategory>()).contains { $0.builtin == nil }
    }

    func join(householdID: UUID, plan: Plan) async throws {
        try await offscreen {
            try deleteSyncedData()
            let state = try SyncState.current(in: context)
            state.householdID = householdID
            state.cursor = 0
            state.plan = plan
            try context.save()
        }
        onHouseholdReady()
    }

    func screenDidClear() {
        screenClearance?.resume()
        screenClearance = nil
    }

    // Rows still on screen would read the models being deleted and crash, so RootView swaps the tabs out first.
    private func offscreen(_ body: () async throws -> Void) async rethrows {
        isResettingLocalData = true
        defer { isResettingLocalData = false }
        if resetsOffscreen {
            await withCheckedContinuation { screenClearance = $0 }
        }
        try await body()
    }

    private func adopt(_ adoption: PendingAdoption) async throws {
        try authenticator.signIn(adoption.session)
        try await join(householdID: adoption.householdID, plan: .free)
    }

    private func resetLocalData() throws {
        try deleteSyncedData()
        for state in try context.fetch(FetchDescriptor<SyncState>()) {
            context.delete(state)
        }
        try context.save()
        try PlaceCategory.insertBuiltinsIfEmpty(into: context)
        pendingHouseholdID = nil
    }

    private func deleteSyncedData() throws {
        for place in try context.fetch(FetchDescriptor<Place>()) {
            context.delete(place)
        }
        for todo in try context.fetch(FetchDescriptor<Todo>()) {
            context.delete(todo)
        }
        for category in try context.fetch(FetchDescriptor<PlaceCategory>()) {
            context.delete(category)
        }
        for membership in try context.fetch(FetchDescriptor<Membership>()) {
            context.delete(membership)
        }
        for write in try context.fetch(FetchDescriptor<PendingWrite>()) {
            context.delete(write)
        }
    }
}
