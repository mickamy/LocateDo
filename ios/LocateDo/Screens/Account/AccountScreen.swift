import OSLog
import SwiftUI

struct AccountScreen: View {
    private enum Action {
        case signOut
        case delete
    }

    @Environment(AccountManager.self) private var account
    @Environment(SyncEngine.self) private var sync

    @State private var failure: LocalizedStringResource?
    @State private var isConfirmingDelete = false
    @State private var isConfirmingSignOut = false
    @State private var hasUnsyncedWrites = false
    @State private var runningAction: Action?

    private let logger = Logger(subsystem: "com.locatedo.LocateDo", category: "account")

    var body: some View {
        Group {
            if account.isSignedIn {
                Form {
                    signedIn
                }
                .trackScreen(.account)
            } else {
                signedOut
            }
        }
        .navigationTitle(Text(.settingsAccountTitle))
        .navigationBarTitleDisplayMode(.inline)
        .disabled(account.isWorking || runningAction != nil)
        .confirmationDialog(
            Text(.settingsAccountSignOutConfirmTitle),
            isPresented: $isConfirmingSignOut,
            titleVisibility: .visible
        ) {
            Button(.settingsAccountSignOut, role: .destructive) {
                Task {
                    await signOut()
                }
            }
        } message: {
            if hasUnsyncedWrites {
                Text(.settingsAccountIosSignOutUnsyncedMessage)
            } else {
                Text(.settingsAccountIosSignOutConfirmMessage)
            }
        }
        .confirmationDialog(
            Text(.settingsAccountDeleteConfirmTitle),
            isPresented: $isConfirmingDelete,
            titleVisibility: .visible
        ) {
            Button(.commonDelete, role: .destructive) {
                Task {
                    await deleteAccount()
                }
            }
        } message: {
            Text(.settingsAccountDeleteConfirmMessage)
        }
    }

    @ViewBuilder
    private var signedIn: some View {
        Section {
            Label(.settingsAccountSignedIn, systemImage: "person.crop.circle.badge.checkmark")
        }
        Section {
            Button {
                Task {
                    await prepareSignOut()
                }
            } label: {
                actionLabel(.settingsAccountSignOut, action: .signOut)
            }
        }
        Section {
            Button(role: .destructive) {
                isConfirmingDelete = true
            } label: {
                actionLabel(.settingsAccountDelete, action: .delete)
            }
        } footer: {
            failureText
        }
    }

    private var signedOut: some View {
        ScrollView {
            AccountBenefits()
        }
        .background(Color(.systemGroupedBackground))
        .safeAreaInset(edge: .bottom) {
            SignInButtons()
                .padding(.horizontal, 20)
                .padding(.bottom, 28)
        }
    }

    @ViewBuilder
    private var failureText: some View {
        if let failure {
            Text(failure)
                .foregroundStyle(.red)
        }
    }

    private func actionLabel(_ title: LocalizedStringResource, action: Action) -> some View {
        HStack {
            Text(title)
            Spacer()
            if runningAction == action {
                ProgressView()
            }
        }
    }

    private func prepareSignOut() async {
        failure = nil
        runningAction = .signOut
        defer { runningAction = nil }

        await sync.drain()
        do {
            hasUnsyncedWrites = try account.hasUnsyncedWrites()
        } catch {
            logger.error("Could not count unsynced writes: \(error, privacy: .public)")
            hasUnsyncedWrites = true
        }
        isConfirmingSignOut = true
    }

    private func signOut() async {
        runningAction = .signOut
        defer { runningAction = nil }

        do {
            try await account.signOut()
        } catch {
            logger.error("Sign out failed: \(error, privacy: .public)")
        }
    }

    private func deleteAccount() async {
        failure = nil
        runningAction = .delete
        defer { runningAction = nil }

        do {
            try await account.deleteAccount()
        } catch {
            logger.error("Account deletion failed: \(error, privacy: .public)")
            failure = .settingsAccountDeleteFailed
        }
    }
}
