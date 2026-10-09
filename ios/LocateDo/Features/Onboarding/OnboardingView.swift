import CoreLocation
import SwiftUI
import UserNotifications

struct OnboardingView: View {
    private enum IntroStage: Int, Comparable {
        case title
        case notification
        case features

        static func < (lhs: Self, rhs: Self) -> Bool {
            lhs.rawValue < rhs.rawValue
        }
    }

    private enum Step: String {
        case intro
        case privacy
        case notifications
    }

    @Environment(AppPreferences.self) private var preferences
    @Environment(LocationProvider.self) private var locationProvider
    @Environment(ArrivalNotifier.self) private var notifier
    @State private var step: Step = .intro
    @State private var isRequesting = false
    @State private var startedAt = Date()
    @State private var introStage = IntroStage.title
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        VStack(spacing: 20) {
            Spacer()
            switch step {
            case .intro:
                intro
            case .privacy:
                privacy
            case .notifications:
                notifications
            }
        }
        .padding(32)
        .animation(.default, value: step)
        .onChange(of: step, initial: true) {
            Analytics.logScreen(.onboarding, parameters: [.step: step.rawValue])
        }
        .onChange(of: locationProvider.authorizationStatus) {
            if step == .privacy, isRequesting, locationProvider.authorizationStatus != .notDetermined {
                advanceToNotifications()
            }
        }
    }

    @ViewBuilder
    private var intro: some View {
        Text(.appName)
            .font(.largeTitle.bold())
        Text(.onboardingTagline)
            .font(.title3)
            .multilineTextAlignment(.center)
        SampleArrivalNotification(isShown: introStage >= .notification)
            .padding(.vertical, 8)
        VStack(alignment: .leading, spacing: 16) {
            feature(.onboardingFeatureArrivalTitle, .onboardingFeatureArrivalMessage, systemImage: "bell.badge")
            feature(.onboardingFeatureListsTitle, .onboardingFeatureListsMessage, systemImage: "cart")
            feature(.onboardingFeatureFamilyTitle, .onboardingFeatureFamilyMessage, systemImage: "person.2")
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .opacity(introStage >= .features ? 1 : 0)
        Spacer()
        Button {
            step = .privacy
        } label: {
            Text(.onboardingStart)
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderedProminent)
        .controlSize(.large)
        .opacity(introStage >= .features ? 1 : 0)
        .accessibilityIdentifier("onboarding.start")
        .task {
            await revealIntro()
        }
    }

    // Top to bottom, so the space waiting for the notification never reads as a gap.
    private func revealIntro() async {
        if reduceMotion {
            introStage = .features
            return
        }
        try? await Task.sleep(for: .milliseconds(400))
        withAnimation(.spring(duration: 0.6, bounce: 0.3)) {
            introStage = .notification
        }
        try? await Task.sleep(for: .milliseconds(500))
        withAnimation(.easeOut(duration: 0.4)) {
            introStage = .features
        }
    }

    private func feature(
        _ title: LocalizedStringResource,
        _ message: LocalizedStringResource,
        systemImage: String
    ) -> some View {
        Label {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.headline)
                Text(message)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        } icon: {
            Image(systemName: systemImage)
                .font(.title3)
                .foregroundStyle(.tint)
                .frame(width: 32)
        }
    }

    // Right before the system asks for location, which is when people wonder where it goes.
    @ViewBuilder
    private var privacy: some View {
        Image(systemName: "lock.circle.fill")
            .font(.system(size: 88))
            .foregroundStyle(.tint)
            .accessibilityHidden(true)
        Text(.onboardingPrivacyTitle)
            .font(.title2.bold())
            .multilineTextAlignment(.center)
        VStack(alignment: .leading, spacing: 16) {
            privacyPoint(.onboardingPrivacyOnDevice, systemImage: "iphone")
            privacyPoint(.onboardingPrivacyNotSent, systemImage: "person.2.slash")
            privacyPoint(.onboardingPrivacyNoAds, systemImage: "hand.raised")
        }
        Spacer()
        Text(.onboardingPermissions)
            .font(.footnote)
            .foregroundStyle(.secondary)
            .multilineTextAlignment(.center)
        Button {
            requestLocation()
        } label: {
            Text(.onboardingAllowLocation)
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderedProminent)
        .controlSize(.large)
        .disabled(isRequesting)
        .accessibilityIdentifier("onboarding.allowLocation")
    }

    private func privacyPoint(_ text: LocalizedStringResource, systemImage: String) -> some View {
        Label {
            Text(text)
                .fixedSize(horizontal: false, vertical: true)
        } icon: {
            Image(systemName: systemImage)
                .foregroundStyle(.tint)
                .frame(width: 28)
        }
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
