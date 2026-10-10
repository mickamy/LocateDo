import SwiftUI

struct OnboardingScreen: View {
    private enum Step: String {
        case intro
        case firstPlace
        case analytics
    }

    private enum Choice: String {
        case addPlace = "add_place"
        case later
    }

    @Environment(AppPreferences.self) private var preferences
    @Environment(AnalyticsConsent.self) private var analyticsConsent
    @Environment(LocationProvider.self) private var locationProvider
    @Environment(ArrivalNotifier.self) private var notifier
    @State private var step: Step = .intro
    @State private var addedKind: StoreKind?
    @State private var startedAt = Date()

    var body: some View {
        Group {
            switch step {
            case .intro:
                VStack(spacing: 20) {
                    Spacer()
                    intro
                }
                .padding(32)
            case .firstPlace:
                FirstPlaceFlow { kind in
                    addedKind = kind
                    if analyticsConsent.needsAnswer {
                        step = .analytics
                    } else {
                        complete()
                    }
                }
            case .analytics:
                VStack(spacing: 20) {
                    Spacer()
                    AnalyticsConsentStep(onAnswered: complete)
                }
                .padding(32)
            }
        }
        .animation(.default, value: step)
        .onChange(of: step, initial: true) {
            if step != .firstPlace {
                Analytics.logScreen(.onboarding, parameters: [.step: step.rawValue])
            }
        }
    }

    @ViewBuilder
    private var intro: some View {
        Text(.appName)
            .font(.largeTitle.bold())
        ArrivalAnimation()
            .frame(height: 280)
            .padding(.vertical, 8)
        Text(.onboardingHeadline)
            .font(.title3.weight(.semibold))
            .multilineTextAlignment(.center)
        Spacer()
        Button {
            step = .firstPlace
        } label: {
            Text(.onboardingStart)
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderedProminent)
        .controlSize(.large)
        .accessibilityIdentifier("onboarding.start")
    }

    private func complete() {
        var parameters: AnalyticsParameters = [
            .choice: (addedKind == nil ? Choice.later : Choice.addPlace).rawValue,
            .locationAuth: DailyState.LocationAuth(locationProvider.authorizationStatus).rawValue,
            .notificationAuth: DailyState.NotificationAuth(notifier.authorizationStatus).rawValue,
            .durationS: max(Int(Date().timeIntervalSince(startedAt)), 0)
        ]
        if let addedKind {
            parameters[.kind] = addedKind.rawValue
        }
        Analytics.log(.onboardingCompleted, parameters: parameters)
        preferences.hasCompletedOnboarding = true
    }
}
