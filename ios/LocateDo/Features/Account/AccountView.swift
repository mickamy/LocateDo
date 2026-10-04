import AuthenticationServices
import OSLog
import SwiftUI

struct AccountView: View {
    private enum Action {
        case signOut
        case delete
    }

    @Environment(AccountManager.self) private var account
    @Environment(SyncEngine.self) private var sync
    @Environment(\.colorScheme) private var colorScheme

    @State private var nonce: String?
    @State private var failure: LocalizedStringResource?
    @State private var isConfirmingDelete = false
    @State private var isConfirmingReplace = false
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
                Text(.settingsAccountSignOutUnsyncedMessage)
            } else {
                Text(.settingsAccountSignOutConfirmMessage)
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
        .alert(Text(.settingsAccountReplaceConfirmTitle), isPresented: $isConfirmingReplace) {
            Button(.settingsAccountReplace, role: .destructive) {
                Task {
                    await replaceLocalData()
                }
            }
            Button(.commonCancel, role: .cancel) {
                account.cancelReplacingLocalData()
            }
        } message: {
            Text(.settingsAccountReplaceConfirmMessage)
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
            AccountBenefitsView()
        }
        .background(Color(.systemGroupedBackground))
        .safeAreaInset(edge: .bottom) {
            VStack(spacing: 12) {
                failureText
                    .font(.footnote)
                    .multilineTextAlignment(.center)
                signInButton
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 28)
        }
    }

    private var signInButton: some View {
        ZStack {
            SignInWithAppleButton(.signIn, onRequest: prepare, onCompletion: complete)
                .signInWithAppleButtonStyle(colorScheme == .dark ? .white : .black)
                .opacity(account.isWorking ? 0 : 1)
            if account.isWorking {
                Capsule()
                    .fill(colorScheme == .dark ? Color.white : Color.black)
                ProgressView()
                    .tint(colorScheme == .dark ? Color.black : Color.white)
            }
        }
        .frame(height: 50)
        .clipShape(.capsule)
    }

    @ViewBuilder
    private var failureText: some View {
        if let failure {
            Text(failure)
                .foregroundStyle(.red)
        }
    }

    private func prepare(_ request: ASAuthorizationAppleIDRequest) {
        let nonce = AppleSignInNonce.make()
        self.nonce = nonce
        failure = nil
        request.requestedScopes = [.fullName]
        request.nonce = AppleSignInNonce.sha256(nonce)
    }

    private func complete(_ result: Result<ASAuthorization, any Error>) {
        switch result {
        case .success(let authorization):
            guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
                  let identityToken = credential.identityToken.flatMap({ String(data: $0, encoding: .utf8) }),
                  let authorizationCode = credential.authorizationCode.flatMap({ String(data: $0, encoding: .utf8) }),
                  let nonce else {
                failure = .settingsAccountSignInFailed
                return
            }
            let displayName = credential.fullName.map { $0.formatted() }
            Task {
                do {
                    try await account.signInWithApple(
                        identityToken: identityToken,
                        authorizationCode: authorizationCode,
                        nonce: nonce,
                        displayName: displayName
                    )
                    isConfirmingReplace = account.needsReplaceConfirmation
                } catch {
                    logger.error("Sign in with Apple failed: \(error, privacy: .public)")
                    failure = .settingsAccountSignInFailed
                }
            }
        case .failure(let error):
            if (error as? ASAuthorizationError)?.code == .canceled {
                return
            }
            logger.error("Apple authorization failed: \(error, privacy: .public)")
            failure = .settingsAccountSignInFailed
        }
    }

    private func replaceLocalData() async {
        do {
            try await account.confirmReplacingLocalData()
        } catch {
            logger.error("Replacing local data failed: \(error, privacy: .public)")
            failure = .settingsAccountSignInFailed
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
