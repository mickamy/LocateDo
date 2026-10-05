import Foundation
import Observation
import OSLog
import SwiftData
import SwiftProtobuf

struct Invite: Identifiable {
    let url: URL
    let expiresAt: Date

    var id: URL {
        url
    }
}

@Observable
final class HouseholdManager {
    private(set) var isWorking = false
    @ObservationIgnored var onRemoved: () async -> Void = {}

    private let household: any Locatedo_Household_V1_HouseholdServiceClientInterface
    private let authenticator: Authenticator
    private let context: ModelContext
    private let account: AccountManager
    private let sync: SyncEngine
    private let logger = Logger(subsystem: "com.locatedo.LocateDo", category: "sharing")

    init(
        household: any Locatedo_Household_V1_HouseholdServiceClientInterface,
        authenticator: Authenticator,
        context: ModelContext,
        account: AccountManager,
        sync: SyncEngine
    ) {
        self.household = household
        self.authenticator = authenticator
        self.context = context
        self.account = account
        self.sync = sync
    }

    func remove(_ userID: UUID) async throws {
        isWorking = true
        defer { isWorking = false }

        try await removeMember(userID)
        await sync.sync()
    }

    func leave() async throws {
        isWorking = true
        defer { isWorking = false }

        guard let userID = authenticator.session?.userID else {
            throw AuthError.signedOut
        }
        await sync.drain()
        try await removeMember(userID)
        try await account.startOver()
    }

    func createInvite() async throws -> Invite {
        isWorking = true
        defer { isWorking = false }

        guard let householdID = try SyncState.current(in: context).householdID else {
            throw AuthError.signedOut
        }
        let request = Locatedo_Household_V1_CreateInviteRequest.with {
            $0.householdID = ProtoInput.id(householdID)
        }
        let client = household
        let response = try await authenticator.authorized { await client.createInvite(request: request, headers: [:]) }
        guard let url = InviteLink.url(for: response.token) else {
            throw URLError(.badURL)
        }
        return Invite(url: url, expiresAt: response.expiresAt.date)
    }

    func accept(token: String) async throws {
        isWorking = true
        defer { isWorking = false }

        await sync.drain()
        let request = Locatedo_Household_V1_AcceptInviteRequest.with {
            $0.token = token
        }
        let client = household
        let response = try await authenticator.authorized { await client.acceptInvite(request: request, headers: [:]) }
        guard let householdID = UUID(uuidString: response.household.id) else {
            throw URLError(.cannotParseResponse)
        }
        var plan = Plan.free
        if response.household.plan == .pro {
            plan = .pro
        }
        try await account.join(householdID: householdID, plan: plan)
        Analytics.log(.inviteAccepted)
    }

    func handleRemoval() async {
        do {
            try await account.startOver()
        } catch {
            logger.error("Could not start over after being removed: \(error, privacy: .public)")
        }
        await onRemoved()
    }

    private func removeMember(_ userID: UUID) async throws {
        guard let householdID = try SyncState.current(in: context).householdID else {
            return
        }
        let request = Locatedo_Household_V1_RemoveMemberRequest.with {
            $0.householdID = ProtoInput.id(householdID)
            $0.userID = ProtoInput.id(userID)
        }
        let client = household
        _ = try await authenticator.authorized { await client.removeMember(request: request, headers: [:]) }
    }
}
