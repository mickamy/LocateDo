import Connect
import Foundation
import Observation
import SwiftData

@Observable
final class AccountManager {
    private(set) var isWorking = false

    private let account: any Locatedo_Account_V1_AccountServiceClientInterface
    private let household: any Locatedo_Household_V1_HouseholdServiceClientInterface
    private let authenticator: Authenticator
    private let context: ModelContext
    private let onLocalDataReset: () async -> Void
    private var pendingHouseholdID: UUID?

    init(
        account: any Locatedo_Account_V1_AccountServiceClientInterface,
        household: any Locatedo_Household_V1_HouseholdServiceClientInterface,
        authenticator: Authenticator,
        context: ModelContext,
        onLocalDataReset: @escaping () async -> Void = {}
    ) {
        self.account = account
        self.household = household
        self.authenticator = authenticator
        self.context = context
        self.onLocalDataReset = onLocalDataReset
    }

    var isSignedIn: Bool {
        authenticator.isSignedIn
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
        try authenticator.signIn(session)

        if response.hasHouseholdID, let householdID = UUID(uuidString: response.householdID) {
            let state = try SyncState.current(in: context)
            state.householdID = householdID
            try context.save()
            return
        }
        try await uploadLocalDataIfNeeded()
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
    }

    func deleteAccount() async throws {
        isWorking = true
        defer { isWorking = false }

        let client = account
        _ = try await authenticator.authorized {
            await client.deleteAccount(request: Locatedo_Account_V1_DeleteAccountRequest(), headers: [:])
        }
        try authenticator.signOut()
        try resetLocalData()
        await onLocalDataReset()
    }

    private func resetLocalData() throws {
        for place in try context.fetch(FetchDescriptor<Place>()) {
            context.delete(place)
        }
        for todo in try context.fetch(FetchDescriptor<Todo>()) {
            context.delete(todo)
        }
        for category in try context.fetch(FetchDescriptor<PlaceCategory>()) {
            context.delete(category)
        }
        for state in try context.fetch(FetchDescriptor<SyncState>()) {
            context.delete(state)
        }
        for write in try context.fetch(FetchDescriptor<PendingWrite>()) {
            context.delete(write)
        }
        try context.save()
        try PlaceCategory.insertBuiltinsIfEmpty(into: context)
        pendingHouseholdID = nil
    }
}
