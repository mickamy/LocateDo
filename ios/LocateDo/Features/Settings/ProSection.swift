import OSLog
import StoreKit
import SwiftData
import SwiftUI

struct ProSection: View {
    @Environment(Entitlements.self) private var entitlements
    @Environment(Authenticator.self) private var authenticator
    @Query private var syncStates: [SyncState]
    @Query private var memberships: [Membership]

    @State private var isShowingPaywall = false
    @State private var isManaging = false
    @State private var isRestoring = false
    @State private var isShowingRestored = false
    @State private var failure: LocalizedStringResource?

    private let logger = Logger(subsystem: "com.locatedo.LocateDo", category: "billing")

    var body: some View {
        Section {
            LabeledContent {
                Text(status)
            } label: {
                Text(.settingsProTitle)
            }
            if entitlements.hasEntitlement {
                Button(.settingsProManage) {
                    isManaging = true
                }
            } else if !isPro && !isMember {
                Button(.settingsProUpgrade) {
                    isShowingPaywall = true
                }
            }
            if !isMember {
                Button {
                    Task {
                        await restore()
                    }
                } label: {
                    HStack {
                        Text(.paywallRestore)
                        Spacer()
                        if isRestoring {
                            ProgressView()
                        }
                    }
                }
                .disabled(isRestoring)
            }
        } footer: {
            if let failure {
                Text(failure)
                    .foregroundStyle(.red)
            }
        }
        .sheet(isPresented: $isShowingPaywall) {
            PaywallView(trigger: .settings)
        }
        .manageSubscriptionsSheet(isPresented: $isManaging)
        .alert(Text(.paywallRestoredTitle), isPresented: $isShowingRestored) {
            Button(.commonOk) {}
        } message: {
            Text(.paywallRestoredMessage)
        }
    }

    private var isPro: Bool {
        Entitlements.isPro(hasEntitlement: entitlements.hasEntitlement, plan: syncStates.first?.plan)
    }

    private var isMember: Bool {
        let me = memberships.first { $0.userID == authenticator.session?.userID }
        return me?.role == .member
    }

    private var status: LocalizedStringResource {
        if isPro {
            return .settingsProActive
        }
        return .settingsProFree
    }

    private func restore() async {
        failure = nil
        isRestoring = true
        defer { isRestoring = false }
        do {
            try await entitlements.restore()
            if entitlements.hasEntitlement {
                isShowingRestored = true
            } else {
                failure = .paywallNothingToRestore
            }
        } catch {
            logger.error("Restore failed: \(error, privacy: .public)")
            failure = .paywallFailed
        }
    }
}
