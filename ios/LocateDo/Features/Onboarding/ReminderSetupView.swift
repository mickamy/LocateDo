import CoreLocation
import SwiftUI
import UIKit
import UserNotifications

// What arrival reminders still need, offered right after a place is saved: notifications and "Always" location, each
// with a check once done. It closes itself when nothing is left.
struct ReminderSetupView: View {
    private enum Answer: String {
        case allow
        case later
        case never
        case dismissed
    }

    let shownCount: Int
    let missing: ReminderSetup.Missing

    @Environment(LocationProvider.self) private var locationProvider
    @Environment(ArrivalNotifier.self) private var notifier
    @Environment(AppPreferences.self) private var preferences
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase
    @State private var shownAt = Date()
    @State private var answer: Answer = .dismissed
    @State private var contentHeight: CGFloat = 520

    private var isComplete: Bool {
        ReminderSetup.missing(location: locationProvider.authorizationStatus,
                              notifications: notifier.authorizationStatus) == nil
    }

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "location.circle.fill")
                .font(.system(size: 64))
                .foregroundStyle(.tint)
                .accessibilityHidden(true)
            Text(.reminderSetupTitle)
                .font(.title2.bold())
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            Text(.reminderSetupDescription)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            VStack(spacing: 12) {
                notificationsRow
                locationRow
            }
            PrivacyNote(text: .alwaysPromptPrivacy)
            Button(.reminderSetupLater) {
                answer = .later
                dismiss()
            }
            .buttonStyle(.bordered)
            .controlSize(.large)
            Button(.reminderSetupNever) {
                answer = .never
                preferences.reminderSetupNever = true
                dismiss()
            }
            .font(.footnote)
            .foregroundStyle(.secondary)
        }
        .trackScreen(.alwaysLocationPrompt)
        .padding(32)
        // As tall as what it says, so no line is cut short whatever the language or text size.
        .onGeometryChange(for: CGFloat.self) { proxy in
            proxy.size.height
        } action: { height in
            contentHeight = height
        }
        .presentationDetents([.height(contentHeight)])
        .presentationDragIndicator(.visible)
        .onChange(of: scenePhase) {
            if scenePhase == .active {
                Task {
                    await refresh()
                }
            }
        }
        .onChange(of: isComplete) {
            if isComplete {
                answer = .allow
                dismiss()
            }
        }
        .onDisappear {
            Analytics.log(.alwaysPromptAnswered, parameters: [
                .result: answer.rawValue,
                .missing: missing.rawValue,
                .shownCount: shownCount,
                .durationS: max(Int(Date().timeIntervalSince(shownAt)), 0)
            ])
        }
    }

    private var notificationsRow: some View {
        row(
            .reminderSetupNotifications,
            systemImage: "bell.badge",
            done: ReminderSetup.needsNotifications(notifier.authorizationStatus) ? nil : .reminderSetupAllowed
        ) {
            if notifier.authorizationStatus == .notDetermined {
                Button(.reminderSetupAllow) {
                    Task {
                        await notifier.requestAuthorization()
                    }
                }
            } else {
                Button(.settingsOpenSettings) {
                    open(UIApplication.openNotificationSettingsURLString)
                }
            }
        }
    }

    private var locationRow: some View {
        row(
            .reminderSetupLocation,
            systemImage: "location",
            done: ReminderSetup.needsAlways(locationProvider.authorizationStatus) ? nil : .settingsLocationAlways
        ) {
            if locationProvider.authorizationStatus == .authorizedWhenInUse, !preferences.hasRequestedAlwaysLocation {
                Button(.reminderSetupAllow) {
                    preferences.hasRequestedAlwaysLocation = true
                    locationProvider.requestAlwaysAuthorization()
                }
            } else {
                Button(.settingsOpenSettings) {
                    open(UIApplication.openSettingsURLString)
                }
            }
        }
    }

    private func row(
        _ title: LocalizedStringResource,
        systemImage: String,
        done: LocalizedStringResource?,
        @ViewBuilder action: () -> some View
    ) -> some View {
        HStack {
            Label(title, systemImage: systemImage)
            Spacer()
            if let done {
                Label(done, systemImage: "checkmark.circle.fill")
                    .foregroundStyle(.green)
                    .font(.subheadline)
            } else {
                action()
                    .buttonStyle(.borderedProminent)
                    .controlSize(.small)
            }
        }
        .padding(12)
        .background(.fill.tertiary, in: RoundedRectangle(cornerRadius: 12))
    }

    private func open(_ string: String) {
        if let url = URL(string: string) {
            openURL(url)
        }
    }

    private func refresh() async {
        locationProvider.refreshAuthorizationStatus()
        await notifier.refreshAuthorizationStatus()
    }
}
