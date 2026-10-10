import OSLog
import SwiftData
import SwiftUI

enum SharingSource: String {
    case home
    case settings
}

struct SharingScreen: View {
    private static let maxMembers = 6

    @Environment(Navigator.self) private var navigator
    @Environment(AccountManager.self) private var account
    @Environment(Authenticator.self) private var authenticator
    @Environment(HouseholdManager.self) private var households
    @Environment(SyncEngine.self) private var sync
    @Environment(Entitlements.self) private var entitlements
    @Environment(\.dismiss) private var dismiss
    @Query(sort: \Membership.joinedAt) private var memberships: [Membership]
    @Query private var syncStates: [SyncState]

    @State private var memberToRemove: Membership?
    @State private var isConfirmingLeave = false
    @State private var failure: LocalizedStringResource?
    @State private var invite: Invite?

    private let logger = Logger(subsystem: "com.locatedo.LocateDo", category: "sharing")

    var body: some View {
        NavigationStack {
            Group {
                if account.isSignedIn {
                    signedIn
                        .trackScreen(.sharing)
                } else {
                    SharingIntro()
                }
            }
            .navigationTitle(Text(.sharingTitle))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(.commonDone) {
                        dismiss()
                    }
                }
            }
        }
    }

    private var signedIn: some View {
        Form {
            if let status {
                Section {
                    SharingHeader(status: status, seatsLeft: seatsLeft) {
                        navigator.present(.paywall(.share))
                    } onInvite: {
                        Task {
                            await createInvite()
                        }
                    }
                }
                .listRowBackground(Color.clear)
            }
            Section {
                ForEach(memberships) { membership in
                    MemberRow(membership: membership, isCurrentUser: membership.userID == currentUserID)
                        .swipeActions {
                            if isOwner && membership.userID != currentUserID {
                                Button(.sharingRemove, role: .destructive) {
                                    memberToRemove = membership
                                }
                            }
                        }
                }
            } header: {
                Text(.sharingMembers)
            } footer: {
                if let failure {
                    Text(failure)
                        .foregroundStyle(.red)
                }
            }
            if memberships.count <= 1 {
                Section {
                    NavigationLink {
                        AcceptInviteScreen()
                    } label: {
                        Label(.sharingAccept, systemImage: "envelope.open")
                    }
                }
            }
            if !isOwner && currentMembership != nil {
                Section {
                    Button(.sharingLeave, role: .destructive) {
                        isConfirmingLeave = true
                    }
                }
            }
        }
        .disabled(households.isWorking)
        .sheet(item: $invite) { invite in
            ActivityView(items: [String(localized: .sharingInviteMessage) + "\n" + invite.url.absoluteString])
                .presentationDetents([.medium, .large])
        }
        .task {
            await sync.sync()
        }
        .refreshable {
            await sync.sync()
        }
        .confirmationDialog(
            Text(.sharingRemoveConfirmTitle(memberToRemove?.shownName ?? "")),
            isPresented: isConfirmingRemove,
            titleVisibility: .visible,
            presenting: memberToRemove
        ) { membership in
            Button(.sharingRemove, role: .destructive) {
                Task {
                    await remove(membership.userID)
                }
            }
        }
        .confirmationDialog(
            Text(.sharingLeaveConfirmTitle),
            isPresented: $isConfirmingLeave,
            titleVisibility: .visible
        ) {
            Button(.sharingLeave, role: .destructive) {
                Task {
                    await leave()
                }
            }
        } message: {
            Text(.sharingIosLeaveConfirmMessage)
        }
    }

    private var status: SharingStatus? {
        guard let currentMembership else {
            return nil
        }
        if currentMembership.role == .member {
            let owner = memberships.first { $0.role == .owner }
            return .member(ownerName: owner?.shownName ?? String(localized: .sharingUnnamedMember))
        }
        if !isPro {
            return .ownerFree
        }
        if memberships.count > 1 {
            return .ownerSharing(count: memberships.count)
        }
        return .ownerAlone
    }

    private var seatsLeft: Int {
        max(Self.maxMembers - memberships.count, 0)
    }

    private var currentUserID: UUID? {
        authenticator.session?.userID
    }

    private var currentMembership: Membership? {
        memberships.first { $0.userID == currentUserID }
    }

    private var isPro: Bool {
        Entitlements.isPro(hasEntitlement: entitlements.hasEntitlement, plan: syncStates.first?.plan)
    }

    private var isOwner: Bool {
        currentMembership?.role == .owner
    }

    private var isConfirmingRemove: Binding<Bool> {
        Binding {
            memberToRemove != nil
        } set: { isPresented in
            if !isPresented {
                memberToRemove = nil
            }
        }
    }

    private func remove(_ userID: UUID) async {
        failure = nil
        do {
            try await households.remove(userID)
        } catch {
            logger.error("Removing a member failed: \(error, privacy: .public)")
            failure = .sharingFailed
        }
    }

    private func createInvite() async {
        failure = nil
        do {
            invite = try await households.createInvite()
        } catch {
            logger.error("Creating an invite failed: \(error, privacy: .public)")
            failure = .sharingFailed
        }
    }

    private func leave() async {
        failure = nil
        do {
            try await households.leave()
        } catch {
            logger.error("Leaving the household failed: \(error, privacy: .public)")
            failure = .sharingFailed
        }
    }
}
