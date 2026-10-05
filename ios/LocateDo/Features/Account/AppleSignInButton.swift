import AuthenticationServices
import OSLog
import SwiftUI

struct AppleSignInButton: View {
    @Environment(AccountManager.self) private var account
    @Environment(\.colorScheme) private var colorScheme

    @State private var nonce: String?
    @State private var failure: LocalizedStringResource?
    @State private var isConfirmingReplace = false

    private let logger = Logger(subsystem: "com.locatedo.LocateDo", category: "account")

    var body: some View {
        VStack(spacing: 12) {
            if let failure {
                Text(failure)
                    .font(.footnote)
                    .foregroundStyle(.red)
                    .multilineTextAlignment(.center)
            }
            button
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

    private var button: some View {
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
        .disabled(account.isWorking)
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
}
