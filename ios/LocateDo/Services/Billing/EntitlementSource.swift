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
    func logIn(_ appUserID: String) async throws -> Bool
    func logOut() async throws -> Bool
    func refresh() async throws -> Bool
    func updates() -> AsyncStream<Bool>
    func plans() async throws -> [PaywallPlan]
    // Returns nil when the buyer cancels.
    func purchase(_ kind: PaywallPlan.Kind) async throws -> Bool?
    func restore() async throws -> Bool
}

final class RevenueCatEntitlementSource: EntitlementSource {
    static let entitlementID = "locatedo_pro"

    private var packages: [PaywallPlan.Kind: Package] = [:]

    init(apiKey: String) {
        Purchases.configure(withAPIKey: apiKey)
    }

    func logIn(_ appUserID: String) async throws -> Bool {
        let (info, _) = try await Purchases.shared.logIn(appUserID)
        return Self.hasPro(info)
    }

    func logOut() async throws -> Bool {
        if Purchases.shared.isAnonymous {
            return try await refresh()
        }
        return Self.hasPro(try await Purchases.shared.logOut())
    }

    func refresh() async throws -> Bool {
        Self.hasPro(try await Purchases.shared.customerInfo())
    }

    func updates() -> AsyncStream<Bool> {
        AsyncStream { continuation in
            let task = Task {
                for await info in Purchases.shared.customerInfoStream {
                    continuation.yield(Self.hasPro(info))
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

    func purchase(_ kind: PaywallPlan.Kind) async throws -> Bool? {
        guard let package = packages[kind] else {
            throw ErrorCode.productNotAvailableForPurchaseError
        }
        let result = try await Purchases.shared.purchase(package: package)
        if result.userCancelled {
            return nil
        }
        return Self.hasPro(result.customerInfo)
    }

    func restore() async throws -> Bool {
        Self.hasPro(try await Purchases.shared.restorePurchases())
    }

    private static func hasPro(_ info: CustomerInfo) -> Bool {
        info.entitlements[entitlementID]?.isActive == true
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
