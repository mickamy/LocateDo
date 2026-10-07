import AuthenticationServices
import GoogleSignIn
import GoogleSignInSwift
import OSLog
import SwiftUI
import UIKit

struct SignInButtons: View {
    @Environment(AccountManager.self) private var account
    @Environment(AppStatusStore.self) private var appStatus
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
            MaintenanceNote()
                .multilineTextAlignment(.center)
            appleButton
            if GoogleSignInSetup.isConfigured {
                googleButton
            }
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

    private var isDisabled: Bool {
        account.isWorking || appStatus.activeMaintenance != nil
    }

    private var googleButton: some View {
        var scheme = GoogleSignInButtonColorScheme.light
        if colorScheme == .dark {
            scheme = .dark
        }
        var state = GoogleSignInButtonState.normal
        if isDisabled {
            state = .disabled
        }
        return GoogleSignInButton(scheme: scheme, style: .wide, state: state) {
            signInWithGoogle()
        }
        .accessibilityIdentifier("account.signInWithGoogle")
    }

    private var appleButton: some View {
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
        .disabled(isDisabled)
    }

    private func prepare(_ request: ASAuthorizationAppleIDRequest) {
        let nonce = SignInNonce.make()
        self.nonce = nonce
        failure = nil
        request.requestedScopes = [.fullName]
        request.nonce = SignInNonce.sha256(nonce)
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

    private func signInWithGoogle() {
        guard let presenter = Self.topViewController() else {
            failure = .settingsAccountSignInFailed
            return
        }
        failure = nil
        let nonce = SignInNonce.make()
        Task {
            do {
                let result = try await GIDSignIn.sharedInstance.signIn(
                    withPresenting: presenter,
                    hint: nil,
                    additionalScopes: nil,
                    nonce: nonce
                )
                GIDSignIn.sharedInstance.signOut()
                guard let idToken = result.user.idToken?.tokenString else {
                    failure = .settingsAccountSignInFailed
                    return
                }
                try await account.signInWithGoogle(idToken: idToken, nonce: nonce)
                isConfirmingReplace = account.needsReplaceConfirmation
            } catch let error as GIDSignInError where error.code == .canceled {
                return
            } catch {
                logger.error("Sign in with Google failed: \(error, privacy: .public)")
                failure = .settingsAccountSignInFailed
            }
        }
    }

    private static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
        var top = scene?.keyWindow?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
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
