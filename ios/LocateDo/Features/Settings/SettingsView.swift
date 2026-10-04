import CoreLocation
import SwiftData
import SwiftUI
import UIKit
import UserNotifications

struct SettingsView: View {
    @Environment(LocationProvider.self) private var locationProvider
    @Environment(ArrivalNotifier.self) private var notifier
    @Environment(AppPreferences.self) private var preferences
    @Environment(AccountManager.self) private var account
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase
    #if DEBUG || STAGING
    @Environment(Authenticator.self) private var authenticator
    @Environment(SyncEngine.self) private var sync
    @Query private var syncStates: [SyncState]
    @Query private var pendingWrites: [PendingWrite]
    @State private var serverStatus: String?
    #endif

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
                    SharingNoticeButton(source: .settings) {
                        Label(.sharingTitle, systemImage: "person.2")
                    }
                }
                Section {
                    LabeledContent {
                        Text(Self.version)
                    } label: {
                        Text(.settingsAboutVersion)
                    }
                    Link(destination: Self.privacyPolicyURL) {
                        Label(.settingsAboutPrivacyPolicy, systemImage: "hand.raised")
                    }
                } header: {
                    Text(.settingsAboutTitle)
                }
                #if DEBUG || STAGING
                debugSection
                #endif
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

    #if DEBUG || STAGING
    private var debugSection: some View {
        Section {
            debugRow("Server", APIEnvironment.current.baseURL.absoluteString)
            debugRow("User", authenticator.session?.userID.uuidString.lowercased() ?? "-")
            debugRow("Household", syncStates.first?.householdID?.uuidString.lowercased() ?? "-")
            debugRow("Cursor", "\(syncStates.first?.cursor ?? 0)")
            debugRow("Queued writes", "\(pendingWrites.count)")
            debugRow("Last pull", sync.lastPullSummary ?? "-")
            Button {
                Task {
                    await sync.sync()
                }
            } label: {
                Text(verbatim: "Sync now")
            }
            Button {
                Task {
                    await checkServer()
                }
            } label: {
                Text(verbatim: "Check connection")
            }
            if let serverStatus {
                Text(verbatim: serverStatus)
                    .foregroundStyle(.secondary)
            }
        } header: {
            Text(verbatim: "Debug")
        }
    }

    private func debugRow(_ label: String, _ value: String) -> some View {
        LabeledContent {
            Text(verbatim: value)
                .font(.footnote.monospaced())
                .textSelection(.enabled)
        } label: {
            Text(verbatim: label)
        }
    }

    private func checkServer() async {
        do {
            let code = try await HealthClient(environment: .current).statusCode()
            serverStatus = "HTTP \(code)"
        } catch {
            serverStatus = error.localizedDescription
        }
    }
    #endif

    private static var privacyPolicyURL: URL {
        if Bundle.main.preferredLocalizations.first == "ja" {
            return URL(string: "https://locatedo.pages.dev/privacy-ja")!
        }
        return URL(string: "https://locatedo.pages.dev/privacy")!
    }

    private static var version: String {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? "-"
        let build = info?["CFBundleVersion"] as? String ?? "-"
        return "\(version) (\(build))"
    }
}
