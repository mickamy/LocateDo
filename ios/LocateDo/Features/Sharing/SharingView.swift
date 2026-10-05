import OSLog
import SwiftData
import SwiftUI

struct SharingView: View {
    private static let maxMembers = 6

    @Environment(AccountManager.self) private var account
    @Environment(Authenticator.self) private var authenticator
    @Environment(HouseholdManager.self) private var households
    @Environment(SyncEngine.self) private var sync
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
                } else {
                    signedOut
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

    private var signedOut: some View {
        VStack(spacing: 24) {
            Spacer()
            Image(systemName: "person.2.circle")
                .font(.system(size: 64))
                .foregroundStyle(.tint)
                .accessibilityHidden(true)
            Text(.sharingSignInMessage)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            Spacer()
            AppleSignInButton()
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 28)
    }

    private var signedIn: some View {
        Form {
            if isOwner && syncStates.first?.plan != .pro {
                Section {
                    Text(.sharingProRequired)
                }
            }
            Section {
                ForEach(memberships) { membership in
                    row(for: membership)
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
            if isOwner && syncStates.first?.plan == .pro {
                Section {
                    Button {
                        Task {
                            await createInvite()
                        }
                    } label: {
                        Label(.sharingInvite, systemImage: "person.badge.plus")
                    }
                    .disabled(memberships.count >= Self.maxMembers)
                } footer: {
                    if memberships.count >= Self.maxMembers {
                        Text(.sharingFull)
                    }
                }
            }
            Section {
                NavigationLink {
                    AcceptInviteView()
                } label: {
                    Label(.sharingAccept, systemImage: "envelope.open")
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
            Text(.sharingRemoveConfirmTitle(memberToRemove.map(name(of:)) ?? "")),
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
            Text(.sharingLeaveConfirmMessage)
        }
    }

    private func row(for membership: Membership) -> some View {
        HStack {
            Text(name(of: membership))
            if membership.userID == currentUserID {
                Text(.sharingYou)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            if membership.role == .owner {
                Text(.sharingOwner)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var currentUserID: UUID? {
        authenticator.session?.userID
    }

    private var currentMembership: Membership? {
        memberships.first { $0.userID == currentUserID }
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

    private func name(of membership: Membership) -> String {
        if membership.displayName.isEmpty {
            return String(localized: .sharingUnnamedMember)
        }
        return membership.displayName
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
