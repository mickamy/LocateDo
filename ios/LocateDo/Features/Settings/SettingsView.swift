import CoreLocation
import SwiftUI
import UIKit
import UserNotifications

struct SettingsView: View {
    @Environment(LocationProvider.self) private var locationProvider
    @Environment(ArrivalNotifier.self) private var notifier
    @Environment(AppPreferences.self) private var preferences
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        @Bindable var preferences = preferences
        NavigationStack {
            Form {
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
                }
                Section {
                    Slider(value: $preferences.defaultRadiusMeters, in: Place.radiusRange, step: 50)
                    Text(DistanceFormatting.string(meters: preferences.defaultRadiusMeters))
                        .frame(maxWidth: .infinity, alignment: .trailing)
                        .foregroundStyle(.secondary)
                } header: {
                    Text(.settingsDefaultRadiusTitle)
                } footer: {
                    Text(.settingsDefaultRadiusLabel)
                }
                Section {
                    LabeledContent {
                        Text(Self.version)
                    } label: {
                        Text(.settingsAboutVersion)
                    }
                } header: {
                    Text(.settingsAboutTitle)
                }
            }
            .navigationTitle(Text(.tabSettings))
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
                locationProvider.start()
            }
        } else {
            Button(.settingsOpenSettings) {
                openSystemSettings()
            }
        }
    }

    @ViewBuilder
    private var notificationAction: some View {
        if notifier.authorizationStatus == .notDetermined {
            Button(.settingsNotificationsAllow) {
                Task {
                    await notifier.requestAuthorization()
                }
            }
        } else {
            Button(.settingsOpenSettings) {
                openSystemSettings()
            }
        }
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
