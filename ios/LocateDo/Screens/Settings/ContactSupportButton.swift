import SwiftData
import SwiftUI
import UIKit

struct ContactSupportButton: View {
    @Environment(Authenticator.self) private var authenticator
    @Environment(Entitlements.self) private var entitlements
    @Environment(AnalyticsConsent.self) private var analyticsConsent
    @Environment(LocationProvider.self) private var locationProvider
    @Environment(ArrivalNotifier.self) private var notifier
    @Environment(\.openURL) private var openURL
    @Query private var syncStates: [SyncState]
    @State private var isShowingFailure = false

    var body: some View {
        Button {
            contact()
        } label: {
            Label(.settingsAboutContact, systemImage: "envelope")
        }
        .alert(Text(.settingsAboutContactFailedTitle), isPresented: $isShowingFailure) {
            Button(.commonOk) {}
        } message: {
            Text(.settingsAboutContactFailedMessage(SupportMail.address))
        }
    }

    private func contact() {
        let details = diagnostics().text
        let body = String(localized: .settingsAboutContactBody) + details
        guard let url = SupportMail.url(subject: String(localized: .settingsAboutContactSubject), body: body) else {
            return
        }
        openURL(url) { accepted in
            if !accepted {
                UIPasteboard.general.string = details
                isShowingFailure = true
            }
        }
    }

    private func diagnostics() -> SupportDiagnostics {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? "-"
        let build = info?["CFBundleVersion"] as? String ?? "-"
        return SupportDiagnostics(
            appVersion: "\(version) (\(build))",
            osVersion: UIDevice.current.systemVersion,
            deviceModel: Self.deviceModel,
            language: Bundle.main.preferredLocalizations.first ?? "-",
            timeZone: TimeZone.current.identifier,
            supportID: Analytics.appInstanceID(),
            sharesUsageData: analyticsConsent.isSending,
            userID: authenticator.session?.userID,
            plan: DailyState.plan(subscription: entitlements.subscription, householdPlan: syncStates.first?.plan),
            locationAuth: DailyState.LocationAuth(locationProvider.authorizationStatus),
            preciseLocation: locationProvider.hasPreciseLocation,
            notificationAuth: DailyState.NotificationAuth(notifier.authorizationStatus),
            backgroundRefresh: Self.backgroundRefresh,
            lowPowerMode: ProcessInfo.processInfo.isLowPowerModeEnabled
        )
    }

    private static var backgroundRefresh: SupportDiagnostics.BackgroundRefresh {
        switch UIApplication.shared.backgroundRefreshStatus {
        case .available: .on
        case .denied: .off
        case .restricted: .restricted
        @unknown default: .off
        }
    }

    private static var deviceModel: String {
        if let simulated = ProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"] {
            return simulated
        }
        var system = utsname()
        uname(&system)
        let machine = withUnsafeBytes(of: system.machine) { bytes in
            String(bytes: bytes.prefix { $0 != 0 }, encoding: .utf8)
        }
        return machine ?? "-"
    }
}
