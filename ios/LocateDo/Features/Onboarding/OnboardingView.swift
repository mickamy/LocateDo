import CoreLocation
import SwiftUI
import UserNotifications

struct OnboardingView: View {
    private enum Step {
        case intro
        case notifications
    }

    @Environment(AppPreferences.self) private var preferences
    @Environment(LocationProvider.self) private var locationProvider
    @Environment(ArrivalNotifier.self) private var notifier
    @State private var step: Step = .intro
    @State private var isRequesting = false
    @State private var startedAt = Date()

    var body: some View {
        VStack(spacing: 20) {
            Spacer()
            switch step {
            case .intro:
                intro
            case .notifications:
                notifications
            }
        }
        .padding(32)
        .animation(.default, value: step)
        .onChange(of: locationProvider.authorizationStatus) {
            if step == .intro, isRequesting, locationProvider.authorizationStatus != .notDetermined {
                advanceToNotifications()
            }
        }
    }

    @ViewBuilder
    private var intro: some View {
        Image(systemName: "mappin.and.ellipse.circle.fill")
            .font(.system(size: 88))
            .foregroundStyle(.tint)
            .accessibilityHidden(true)
        Text(.appName)
            .font(.largeTitle.bold())
        Text(.onboardingTagline)
            .font(.title3)
            .multilineTextAlignment(.center)
        Text(.onboardingDescription)
            .foregroundStyle(.secondary)
            .multilineTextAlignment(.center)
        Spacer()
        Text(.onboardingPermissions)
            .font(.footnote)
            .foregroundStyle(.secondary)
            .multilineTextAlignment(.center)
        Button {
            requestLocation()
        } label: {
            Text(.onboardingStart)
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderedProminent)
        .controlSize(.large)
        .disabled(isRequesting)
        .accessibilityIdentifier("onboarding.start")
    }

    @ViewBuilder
    private var notifications: some View {
        Image(systemName: "bell.badge.circle.fill")
            .font(.system(size: 88))
            .foregroundStyle(.tint)
            .accessibilityHidden(true)
        Text(.onboardingNotificationsTitle)
            .font(.title2.bold())
            .multilineTextAlignment(.center)
        Text(.onboardingNotificationsDescription)
            .foregroundStyle(.secondary)
            .multilineTextAlignment(.center)
        Spacer()
        Button {
            requestNotifications()
        } label: {
            Text(.onboardingAllowNotifications)
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderedProminent)
        .controlSize(.large)
        .disabled(isRequesting)
        .accessibilityIdentifier("onboarding.allowNotifications")
        Button(.onboardingLater) {
            finish()
        }
        .disabled(isRequesting)
    }

    private func requestLocation() {
        isRequesting = true
        locationProvider.start()
        if locationProvider.authorizationStatus != .notDetermined {
            advanceToNotifications()
        }
    }

    private func advanceToNotifications() {
        isRequesting = false
        if notifier.authorizationStatus == .notDetermined {
            step = .notifications
        } else {
            finish()
        }
    }

    private func requestNotifications() {
        isRequesting = true
        Task {
            await notifier.requestAuthorization()
            finish()
        }
    }

    private func finish() {
        Analytics.log(.onboardingCompleted, parameters: [
            .locationAuth: DailyState.LocationAuth(locationProvider.authorizationStatus).rawValue,
            .notificationAuth: DailyState.NotificationAuth(notifier.authorizationStatus).rawValue,
            .durationS: max(Int(Date().timeIntervalSince(startedAt)), 0)
        ])
        preferences.hasCompletedOnboarding = true
    }
}
