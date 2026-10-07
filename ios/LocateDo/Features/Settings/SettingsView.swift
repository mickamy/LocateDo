import CoreLocation
import SwiftUI
import UIKit
import UserNotifications

struct SettingsView: View {
    @Environment(LocationProvider.self) private var locationProvider
    @Environment(ArrivalNotifier.self) private var notifier
    @Environment(AppPreferences.self) private var preferences
    @Environment(AccountManager.self) private var account
    @Environment(PromotionsConsent.self) private var promotionsConsent
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        @Bindable var preferences = preferences
        NavigationStack {
            Form {
                Section {
                    NavigationLink {
                        AccountView()
                    } label: {
                        LabeledContent {
                            Text(account.isSignedIn ? .settingsAccountSignedIn : .settingsAccountNotSignedIn)
                        } label: {
                            Label(.settingsAccountTitle, systemImage: "person.crop.circle")
                        }
                    }
                }
                Section {
                    LabeledContent {
                        Text(locationStatus)
                    } label: {
                        Label(.settingsLocationTitle, systemImage: "location")
                    }
                    if locationProvider.authorizationStatus != .authorizedAlways {
                        Text(.settingsLocationNeedsAlways)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                        locationAction
                    }
                }
                Section {
                    LabeledContent {
                        Text(notificationStatus)
                    } label: {
                        Label(.settingsNotificationsTitle, systemImage: "bell")
                    }
                    if notifier.authorizationStatus != .authorized {
                        Text(.settingsNotificationsNeedsAllow)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                        notificationAction
                    }
                    Toggle(isOn: promotionsConsentBinding) {
                        Text(.settingsNotificationsPromotionsTitle)
                    }
                    .accessibilityIdentifier("settings.promotionsConsent")
                } footer: {
                    Text(.settingsNotificationsPromotionsFooter)
                }
                Section {
                    Slider(value: $preferences.defaultRadiusMeters, in: Place.radiusRange, step: 50) {
                        Text(.settingsDefaultRadiusLabel)
                    }
                    .accessibilityValue(Text(DistanceFormatting.string(meters: preferences.defaultRadiusMeters)))
                    Text(DistanceFormatting.string(meters: preferences.defaultRadiusMeters))
                        .frame(maxWidth: .infinity, alignment: .trailing)
                        .foregroundStyle(.secondary)
                        .accessibilityHidden(true)
                } header: {
                    Text(.settingsDefaultRadiusTitle)
                } footer: {
                    Text(.settingsDefaultRadiusLabel)
                }
                Section {
                    NavigationLink {
                        CategoriesView()
                    } label: {
                        Label(.categoryTitle, systemImage: "tag")
                    }
                }
                Section {
                    SharingButton(source: .settings) {
                        Label(.sharingTitle, systemImage: "person.2")
                    }
                }
                ProSection()
                Section {
                    LabeledContent {
                        Text(Self.version)
                    } label: {
                        Text(.settingsAboutVersion)
                    }
                    Link(destination: LegalLinks.privacyPolicy) {
                        Label(.settingsAboutPrivacyPolicy, systemImage: "hand.raised")
                    }
                } header: {
                    Text(.settingsAboutTitle)
                }
                #if DEBUG || STAGING
                DebugSection()
                #endif
            }
            .trackScreen(.settings)
            .navigationTitle(Text(.tabSettings))
            .maintenanceBanner()
            .task {
                await refresh()
            }
            .onChange(of: scenePhase) {
                if scenePhase == .active {
                    Task {
                        await refresh()
                    }
                }
            }
        }
    }

    private var promotionsConsentBinding: Binding<Bool> {
        Binding {
            promotionsConsent.isOn
        } set: { isOn in
            promotionsConsent.set(isOn, source: .settings)
        }
    }

    private var locationStatus: LocalizedStringResource {
        switch locationProvider.authorizationStatus {
        case .authorizedAlways: .settingsLocationAlways
        case .authorizedWhenInUse: .settingsLocationWhenInUse
        case .denied, .restricted: .settingsLocationDenied
        case .notDetermined: .settingsLocationNotDetermined
        @unknown default: .settingsLocationNotDetermined
        }
    }

    private var notificationStatus: LocalizedStringResource {
        switch notifier.authorizationStatus {
        case .authorized, .provisional, .ephemeral: .settingsNotificationsAuthorized
        case .denied: .settingsNotificationsDenied
        case .notDetermined: .settingsNotificationsNotDetermined
        @unknown default: .settingsNotificationsNotDetermined
        }
    }

    @ViewBuilder
    private var locationAction: some View {
        if locationProvider.authorizationStatus == .notDetermined {
            Button(.settingsLocationAllow) {
                Self.logAction(.location, .request)
                locationProvider.start()
            }
        } else {
            Button(.settingsOpenSettings) {
                Self.logAction(.location, .openSettings)
                openSystemSettings()
            }
        }
    }

    @ViewBuilder
    private var notificationAction: some View {
        if notifier.authorizationStatus == .notDetermined {
            Button(.settingsNotificationsAllow) {
                Self.logAction(.notifications, .request)
                Task {
                    await notifier.requestAuthorization()
                }
            }
            .accessibilityIdentifier("settings.allowNotifications")
        } else {
            Button(.settingsOpenSettings) {
                Self.logAction(.notifications, .openSettings)
                openSystemSettings()
            }
        }
    }

    private enum Permission: String {
        case location
        case notifications
    }

    private enum PermissionAction: String {
        case request
        case openSettings = "open_settings"
    }

    private static func logAction(_ permission: Permission, _ action: PermissionAction) {
        Analytics.log(.permissionActionTapped, parameters: [.kind: permission.rawValue, .action: action.rawValue])
    }

    private func refresh() async {
        locationProvider.refreshAuthorizationStatus()
        await notifier.refreshAuthorizationStatus()
    }

    private func openSystemSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) {
            openURL(url)
        }
    }

    private static var version: String {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? "-"
        let build = info?["CFBundleVersion"] as? String ?? "-"
        return "\(version) (\(build))"
    }
}
