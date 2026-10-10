import SwiftUI

struct SettingsScreen: View {
    @Environment(Navigator.self) private var navigator
    @Environment(AppPreferences.self) private var preferences
    @Environment(AccountManager.self) private var account
    @Environment(LocationProvider.self) private var locationProvider
    @Environment(ArrivalNotifier.self) private var notifier
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    let entry: ScreenEntry

    var body: some View {
        @Bindable var preferences = preferences
        NavigationStack {
            Form {
                Section {
                    NavigationLink {
                        AccountScreen()
                    } label: {
                        LabeledContent {
                            Text(account.isSignedIn ? .settingsAccountSignedIn : .settingsAccountNotSignedIn)
                        } label: {
                            Label(.settingsAccountTitle, systemImage: "person.crop.circle")
                        }
                    }
                }
                LocationSection()
                NotificationsSection()
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
                        CategoriesScreen()
                    } label: {
                        Label(.categoryTitle, systemImage: "tag")
                    }
                }
                Section {
                    Button {
                        Analytics.log(.shareTapped, parameters: [.source: SharingSource.settings.rawValue])
                        navigator.present(.sharing)
                    } label: {
                        Label(.sharingTitle, systemImage: "person.2")
                    }
                }
                ProSection()
                UsageDataSection()
                AboutSection()
                #if DEBUG || STAGING
                DebugSection()
                #endif
            }
            .trackScreen(.settings, opening: entry.parameters)
            .navigationTitle(Text(.tabSettings))
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(.commonDone) {
                        dismiss()
                    }
                    .accessibilityIdentifier("settings.done")
                }
            }
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

    private func refresh() async {
        locationProvider.refreshAuthorizationStatus()
        await notifier.refreshAuthorizationStatus()
    }
}
