import AuthenticationServices
import OSLog
import SwiftUI

struct AccountView: View {
    @Environment(AccountManager.self) private var account
    @Environment(\.colorScheme) private var colorScheme

    @State private var nonce: String?
    @State private var failure: LocalizedStringResource?
    @State private var isConfirmingDelete = false

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
        .disabled(account.isWorking)
        .overlay {
            if account.isWorking {
                ProgressView()
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
            Button(.settingsAccountDelete, role: .destructive) {
                isConfirmingDelete = true
            }
        } footer: {
            failureText
        }
    }

    private var signedOut: some View {
        ScrollView {
            VStack(spacing: 32) {
                VStack(spacing: 12) {
                    Image(systemName: "person.crop.circle")
                        .font(.system(size: 64))
                        .foregroundStyle(.tint)
                        .accessibilityHidden(true)
                    Text(.settingsAccountDescription)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }
                VStack(alignment: .leading, spacing: 24) {
                    benefit(
                        systemImage: "iphone.and.arrow.forward",
                        title: .settingsAccountBenefitsSyncTitle,
                        message: .settingsAccountBenefitsSyncMessage
                    )
                    benefit(
                        systemImage: "person.2",
                        title: .settingsAccountBenefitsShareTitle,
                        message: .settingsAccountBenefitsShareMessage
                    )
                    benefit(
                        systemImage: "lock.shield",
                        title: .settingsAccountBenefitsPrivacyTitle,
                        message: .settingsAccountBenefitsPrivacyMessage
                    )
                }
            }
            .padding(.horizontal, 32)
            .padding(.vertical, 40)
            .frame(maxWidth: .infinity)
        }
        .background(Color(.systemGroupedBackground))
        .safeAreaInset(edge: .bottom) {
            VStack(spacing: 12) {
                failureText
                    .font(.footnote)
                    .multilineTextAlignment(.center)
                SignInWithAppleButton(.signIn, onRequest: prepare, onCompletion: complete)
                    .signInWithAppleButtonStyle(colorScheme == .dark ? .white : .black)
                    .frame(height: 50)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 16)
        }
    }

    private func benefit(
        systemImage: String,
        title: LocalizedStringResource,
        message: LocalizedStringResource
    ) -> some View {
        HStack(alignment: .top, spacing: 16) {
            Image(systemName: systemImage)
                .font(.title2)
                .foregroundStyle(.tint)
                .frame(width: 32)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.headline)
                Text(message)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .accessibilityElement(children: .combine)
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

    private func deleteAccount() async {
        failure = nil
        do {
            try await account.deleteAccount()
        } catch {
            logger.error("Account deletion failed: \(error, privacy: .public)")
            failure = .settingsAccountDeleteFailed
        }
    }
}
