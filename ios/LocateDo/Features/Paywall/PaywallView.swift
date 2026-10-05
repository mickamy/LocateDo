import OSLog
import SwiftData
import SwiftUI

enum PaywallTrigger: String, Identifiable {
    case placeLimit = "place_limit"
    case todoLimit = "todo_limit"
    case share
    case settings

    var id: String {
        rawValue
    }
}

struct PaywallView: View {
    let trigger: PaywallTrigger

    @Environment(Entitlements.self) private var entitlements
    @Environment(Authenticator.self) private var authenticator
    @Environment(\.dismiss) private var dismiss
    @Query private var memberships: [Membership]

    @State private var plans: [PaywallPlan] = []
    @State private var selected: PaywallPlan.Kind = .annual
    @State private var isLoading = true
    @State private var isWorking = false
    @State private var failure: LocalizedStringResource?

    private let logger = Logger(subsystem: "com.locatedo.LocateDo", category: "billing")

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 28) {
                    header
                    benefits
                    if isMember {
                        Text(.paywallMemberMessage)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                    } else {
                        purchase
                    }
                }
                .padding(24)
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(role: .close) {
                        dismiss()
                    }
                }
            }
        }
        .task {
            await loadPlans()
        }
        .onChange(of: entitlements.hasEntitlement) {
            if entitlements.hasEntitlement {
                dismiss()
            }
        }
    }

    private var header: some View {
        VStack(spacing: 12) {
            Image(systemName: "star.circle.fill")
                .font(.system(size: 64))
                .foregroundStyle(.tint)
                .accessibilityHidden(true)
            Text(.paywallTitle)
                .font(.largeTitle.bold())
            if let reason {
                Text(reason)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
        }
    }

    private var benefits: some View {
        VStack(alignment: .leading, spacing: 14) {
            benefit(.paywallBenefitShare, systemImage: "person.2")
            benefit(.paywallBenefitUnlimited, systemImage: "infinity")
            benefit(.paywallBenefitHousehold, systemImage: "house")
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func benefit(_ text: LocalizedStringResource, systemImage: String) -> some View {
        Label {
            Text(text)
        } icon: {
            Image(systemName: systemImage)
                .foregroundStyle(.tint)
        }
    }

    @ViewBuilder
    private var purchase: some View {
        if isLoading {
            ProgressView()
        } else if plans.isEmpty {
            Text(.paywallUnavailable)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        } else {
            VStack(spacing: 12) {
                ForEach(plans) { plan in
                    PaywallPlanRow(plan: plan, isSelected: plan.kind == selected) {
                        selected = plan.kind
                    }
                }
            }
            Button {
                Task {
                    await buy()
                }
            } label: {
                ZStack {
                    Text(primaryTitle)
                        .opacity(isWorking ? 0 : 1)
                    if isWorking {
                        ProgressView()
                    }
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .disabled(isWorking)
            if let failure {
                Text(failure)
                    .font(.footnote)
                    .foregroundStyle(.red)
                    .multilineTextAlignment(.center)
            }
            footer
        }
    }

    private var footer: some View {
        VStack(spacing: 12) {
            Text(.paywallRenewalNote)
                .font(.caption)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            HStack(spacing: 16) {
                Button(.paywallRestore) {
                    Task {
                        await restore()
                    }
                }
                Link(destination: LegalLinks.termsOfUse) {
                    Text(.paywallTerms)
                }
                Link(destination: LegalLinks.privacyPolicy) {
                    Text(.settingsAboutPrivacyPolicy)
                }
            }
            .font(.caption)
            .disabled(isWorking)
        }
    }

    private var reason: LocalizedStringResource? {
        switch trigger {
        case .placeLimit: .paywallReasonPlaces
        case .todoLimit: .paywallReasonTodos
        case .share: .paywallReasonShare
        case .settings: nil
        }
    }

    private var primaryTitle: LocalizedStringResource {
        if plans.first(where: { $0.kind == selected })?.trialDays != nil {
            return .paywallStartTrial
        }
        return .paywallSubscribe
    }

    private var isMember: Bool {
        let me = memberships.first { $0.userID == authenticator.session?.userID }
        return me?.role == .member
    }

    private func loadPlans() async {
        defer { isLoading = false }
        do {
            plans = try await entitlements.plans()
        } catch {
            logger.error("Loading plans failed: \(error, privacy: .public)")
        }
    }

    private func buy() async {
        failure = nil
        isWorking = true
        defer { isWorking = false }
        do {
            _ = try await entitlements.purchase(selected)
        } catch {
            logger.error("Purchase failed: \(error, privacy: .public)")
            failure = .paywallFailed
        }
    }

    private func restore() async {
        failure = nil
        isWorking = true
        defer { isWorking = false }
        do {
            try await entitlements.restore()
            if !entitlements.hasEntitlement {
                failure = .paywallNothingToRestore
            }
        } catch {
            logger.error("Restore failed: \(error, privacy: .public)")
            failure = .paywallFailed
        }
    }
}
