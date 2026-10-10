import Foundation
import RevenueCat

struct PaywallPlan: Identifiable, Equatable {
    enum Kind {
        case annual
        case monthly
    }

    let kind: Kind
    let price: String
    let trialDays: Int?

    var id: Kind {
        kind
    }
}

protocol EntitlementSource {
    func logIn(_ appUserID: String) async throws -> ProSubscription?
    func logOut() async throws -> ProSubscription?
    func refresh() async throws -> ProSubscription?
    func updates() -> AsyncStream<ProSubscription?>
    func plans() async throws -> [PaywallPlan]
    func purchase(_ kind: PaywallPlan.Kind) async throws -> PurchaseOutcome
    func restore() async throws -> ProSubscription?
    func analyticsIDChanged()
}

final class RevenueCatEntitlementSource: EntitlementSource {
    static let entitlementID = "locatedo_pro"

    private var packages: [PaywallPlan.Kind: Package] = [:]

    init(apiKey: String) {
        Purchases.configure(withAPIKey: apiKey)
        Self.attachAnalyticsID()
    }

    func logIn(_ appUserID: String) async throws -> ProSubscription? {
        let (info, _) = try await Purchases.shared.logIn(appUserID)
        Self.attachAnalyticsID()
        return Self.subscription(in: info)
    }

    func logOut() async throws -> ProSubscription? {
        if Purchases.shared.isAnonymous {
            return try await refresh()
        }
        let info = try await Purchases.shared.logOut()
        Self.attachAnalyticsID()
        return Self.subscription(in: info)
    }

    func refresh() async throws -> ProSubscription? {
        Self.subscription(in: try await Purchases.shared.customerInfo())
    }

    func updates() -> AsyncStream<ProSubscription?> {
        AsyncStream { continuation in
            let task = Task {
                for await info in Purchases.shared.customerInfoStream {
                    continuation.yield(Self.subscription(in: info))
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in
                task.cancel()
            }
        }
    }

    func plans() async throws -> [PaywallPlan] {
        guard let offering = try await Purchases.shared.offerings().current else {
            return []
        }
        var found: [PaywallPlan.Kind: Package] = [:]
        if let annual = offering.annual {
            found[.annual] = annual
        }
        if let monthly = offering.monthly {
            found[.monthly] = monthly
        }
        packages = found
        let eligibility = await Purchases.shared.checkTrialOrIntroDiscountEligibility(packages: Array(found.values))
        return [PaywallPlan.Kind.annual, .monthly].compactMap { kind in
            guard let package = found[kind] else {
                return nil
            }
            return PaywallPlan(
                kind: kind,
                price: package.storeProduct.localizedPriceString,
                trialDays: Self.trialDays(of: package, eligible: eligibility[package]?.status == .eligible)
            )
        }
    }

    func purchase(_ kind: PaywallPlan.Kind) async throws -> PurchaseOutcome {
        guard let package = packages[kind] else {
            throw ErrorCode.productNotAvailableForPurchaseError
        }
        let result = try await Purchases.shared.purchase(package: package)
        if result.userCancelled {
            return .cancelled
        }
        return .completed(Self.subscription(in: result.customerInfo))
    }

    func restore() async throws -> ProSubscription? {
        Self.subscription(in: try await Purchases.shared.restorePurchases())
    }

    func analyticsIDChanged() {
        Self.attachAnalyticsID()
    }

    // Attributes belong to the current App User ID; without it the Firebase integration skips the user.
    private static func attachAnalyticsID() {
        Purchases.shared.attribution.setFirebaseAppInstanceID(Analytics.appInstanceID())
    }

    private static func subscription(in info: CustomerInfo) -> ProSubscription? {
        guard let entitlement = info.entitlements[entitlementID], entitlement.isActive else {
            return nil
        }
        return ProSubscription(
            term: ProSubscription.term(forProductID: entitlement.productIdentifier),
            expiresAt: entitlement.expirationDate,
            willRenew: entitlement.willRenew,
            isTrial: entitlement.periodType == .trial,
            hasBillingIssue: entitlement.billingIssueDetectedAt != nil
        )
    }

    private static func trialDays(of package: Package, eligible: Bool) -> Int? {
        guard eligible,
              let discount = package.storeProduct.introductoryDiscount,
              discount.paymentMode == .freeTrial else {
            return nil
        }
        let period = discount.subscriptionPeriod
        switch period.unit {
        case .day: return period.value
        case .week: return period.value * 7
        case .month: return period.value * 30
        case .year: return period.value * 365
        @unknown default: return period.value
        }
    }
}
