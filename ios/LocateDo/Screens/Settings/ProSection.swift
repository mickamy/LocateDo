import OSLog
import StoreKit
import SwiftData
import SwiftUI

struct ProSection: View {
    @Environment(Navigator.self) private var navigator
    @Environment(Entitlements.self) private var entitlements
    @Environment(Authenticator.self) private var authenticator
    @Query private var syncStates: [SyncState]
    @Query private var memberships: [Membership]

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
            ForEach(details, id: \.self) { detail in
                row(for: detail)
            }
            if entitlements.hasEntitlement {
                Button(.settingsProManage) {
                    isManaging = true
                }
            } else if !isPro && !isMember {
                Button(.settingsProUpgrade) {
                    navigator.present(.paywall(.settings))
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
        .manageSubscriptionsSheet(isPresented: $isManaging)
        .alert(Text(.paywallRestoredTitle), isPresented: $isShowingRestored) {
            Button(.commonOk) {}
        } message: {
            Text(.paywallRestoredMessage)
        }
    }

    private var details: [ProDetail] {
        ProDetail.details(subscription: entitlements.subscription, plan: syncStates.first?.plan)
    }

    @ViewBuilder
    private func row(for detail: ProDetail) -> some View {
        switch detail {
        case .term(let term, let isTrial):
            LabeledContent {
                if isTrial {
                    Text(.settingsProTrial(String(localized: termName(term))))
                } else {
                    Text(termName(term))
                }
            } label: {
                Text(.settingsProTerm)
            }
        case .renews(let date):
            LabeledContent {
                Text(date, format: .dateTime.year().month().day())
            } label: {
                Text(.settingsProRenews)
            }
        case .ends(let date):
            LabeledContent {
                Text(date, format: .dateTime.year().month().day())
            } label: {
                Text(.settingsProEnds)
            }
        case .autoRenewOff:
            Text(.settingsProAutoRenewOff)
                .font(.footnote)
                .foregroundStyle(.secondary)
        case .billingIssue:
            Text(.settingsProBillingIssue)
                .font(.footnote)
                .foregroundStyle(.red)
        case .household:
            Text(.settingsProHousehold)
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }

    private func termName(_ term: ProSubscription.Term) -> LocalizedStringResource {
        switch term {
        case .annual:
            .settingsProAnnual
        case .monthly:
            .settingsProMonthly
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
