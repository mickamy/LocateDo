import CoreLocation
import SwiftUI
import UIKit
import UserNotifications

private enum Permission: String {
    case location
    case preciseLocation = "precise_location"
    case notifications
}

private enum PermissionAction: String {
    case request
    case openSettings = "open_settings"
}

private func logAction(_ permission: Permission, _ action: PermissionAction) {
    Analytics.log(.permissionActionTapped, parameters: [.kind: permission.rawValue, .action: action.rawValue])
}

private struct OpenSystemSettingsButton: View {
    @Environment(\.openURL) private var openURL
    let permission: Permission

    var body: some View {
        Button(.settingsOpenSettings) {
            logAction(permission, .openSettings)
            if let url = URL(string: UIApplication.openSettingsURLString) {
                openURL(url)
            }
        }
    }
}

struct LocationSection: View {
    @Environment(LocationProvider.self) private var locationProvider

    var body: some View {
        Section {
            LabeledContent {
                Text(status)
            } label: {
                Label(.settingsLocationTitle, systemImage: "location")
            }
            if locationProvider.authorizationStatus != .authorizedAlways {
                Text(.settingsLocationIosNeedsAlways)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                if locationProvider.authorizationStatus == .notDetermined {
                    Button(.settingsLocationAllow) {
                        logAction(.location, .request)
                        locationProvider.start()
                    }
                } else {
                    OpenSystemSettingsButton(permission: .location)
                }
            } else if !locationProvider.hasPreciseLocation {
                Text(.settingsLocationNeedsPrecise)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                OpenSystemSettingsButton(permission: .preciseLocation)
            }
        }
    }

    private var status: LocalizedStringResource {
        switch locationProvider.authorizationStatus {
        case .authorizedAlways: .settingsLocationAlways
        case .authorizedWhenInUse: .settingsLocationWhenInUse
        case .denied, .restricted: .settingsLocationDenied
        case .notDetermined: .settingsLocationNotDetermined
        @unknown default: .settingsLocationNotDetermined
        }
    }
}

struct NotificationsSection: View {
    @Environment(ArrivalNotifier.self) private var notifier
    @Environment(AccountManager.self) private var account
    @Environment(PromotionsConsent.self) private var promotionsConsent
    @Environment(CompletionNotices.self) private var completionNotices

    var body: some View {
        Section {
            LabeledContent {
                Text(status)
            } label: {
                Label(.settingsNotificationsTitle, systemImage: "bell")
            }
            if notifier.authorizationStatus != .authorized {
                Text(.settingsNotificationsNeedsAllow)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                if notifier.authorizationStatus == .notDetermined {
                    Button(.settingsNotificationsAllow) {
                        logAction(.notifications, .request)
                        Task {
                            await notifier.requestAuthorization()
                        }
                    }
                    .accessibilityIdentifier("settings.allowNotifications")
                } else {
                    OpenSystemSettingsButton(permission: .notifications)
                }
            }
            Toggle(isOn: promotionsConsentBinding) {
                Text(.settingsNotificationsPromotionsTitle)
            }
            .accessibilityIdentifier("settings.promotionsConsent")
        } footer: {
            Text(.settingsNotificationsPromotionsFooter)
        }
        if account.isSignedIn {
            Section {
                Toggle(isOn: completionNoticesBinding) {
                    Text(.settingsNotificationsCompletionTitle)
                }
                .accessibilityIdentifier("settings.completionNotices")
            } footer: {
                Text(.settingsNotificationsCompletionFooter)
            }
        }
    }

    private var status: LocalizedStringResource {
        switch notifier.authorizationStatus {
        case .authorized, .provisional, .ephemeral: .settingsNotificationsAuthorized
        case .denied: .settingsNotificationsDenied
        case .notDetermined: .settingsNotificationsNotDetermined
        @unknown default: .settingsNotificationsNotDetermined
        }
    }

    private var promotionsConsentBinding: Binding<Bool> {
        Binding {
            promotionsConsent.isOn
        } set: { isOn in
            promotionsConsent.set(isOn, source: .settings)
        }
    }

    private var completionNoticesBinding: Binding<Bool> {
        Binding {
            completionNotices.isOn
        } set: { isOn in
            completionNotices.set(isOn)
        }
    }
}
