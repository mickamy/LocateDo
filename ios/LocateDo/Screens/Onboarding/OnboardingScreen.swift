import SwiftUI

struct OnboardingScreen: View {
    private enum Step: String {
        case intro
        case firstPlace
        case returning
        case privacy
        case analytics
    }

    private enum Choice: String {
        case addPlace = "add_place"
        case later
        case invite
        case signIn = "sign_in"
    }

    @Environment(AppPreferences.self) private var preferences
    @Environment(AnalyticsConsent.self) private var analyticsConsent
    @Environment(LocationProvider.self) private var locationProvider
    @Environment(ArrivalNotifier.self) private var notifier
    @Environment(Navigator.self) private var navigator
    @State private var step: Step = .intro
    @State private var choice = Choice.later
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
                    choice = kind == nil ? .later : .addPlace
                    askConsentOrComplete()
                }
            case .returning:
                ReturningStep {
                    choose(.invite)
                } onSignIn: {
                    choose(.signIn)
                } onBack: {
                    step = .intro
                }
            case .privacy:
                PrivacyStep(reason: .firstPlaceLaterPermissions, onDone: askConsentOrComplete)
            case .analytics:
                OnboardingPage { isSpaced in
                    if isSpaced {
                        Spacer()
                    }
                    AnalyticsConsentStep(onAnswered: complete)
                }
            }
        }
        .animation(.default, value: step)
        .onChange(of: step, initial: true) {
            if [.intro, .returning, .analytics].contains(step) {
                Analytics.logScreen(.onboarding, parameters: [.step: step.rawValue])
            }
        }
        // An invite link opened mid-onboarding: the household's places replace a first place, so skip to it.
        .onChange(of: navigator.hasInvite, initial: true) {
            if navigator.hasInvite, [.intro, .firstPlace, .returning].contains(step) {
                choose(.invite)
            }
        }
    }

    @ViewBuilder
    private var intro: some View {
        VStack(spacing: 8) {
            Image("AppMark")
                .resizable()
                .frame(width: 56, height: 56)
                .clipShape(RoundedRectangle(cornerRadius: 13))
                .accessibilityHidden(true)
            Text(.appName)
                .font(.title2.bold())
        }
        // Shrinks on small screens before anything else is pushed off.
        ArrivalAnimation()
            .frame(minHeight: 160, maxHeight: 260)
            .padding(.vertical, 8)
        VStack(spacing: 8) {
            Text(.onboardingHeadline)
                .font(.title.bold())
                .fixedSize(horizontal: false, vertical: true)
            Text(.onboardingSubheadline)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
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
        Button(.onboardingReturningLink) {
            step = .returning
        }
        .font(.subheadline)
        .accessibilityIdentifier("onboarding.returning")
    }

    private func choose(_ choice: Choice) {
        self.choice = choice
        if locationProvider.authorizationStatus == .notDetermined {
            step = .privacy
            return
        }
        askConsentOrComplete()
    }

    private func askConsentOrComplete() {
        if analyticsConsent.needsAnswer {
            step = .analytics
            return
        }
        complete()
    }

    private func complete() {
        var parameters: AnalyticsParameters = [
            .choice: choice.rawValue,
            .locationAuth: DailyState.LocationAuth(locationProvider.authorizationStatus).rawValue,
            .notificationAuth: DailyState.NotificationAuth(notifier.authorizationStatus).rawValue,
            .durationS: max(Int(Date().timeIntervalSince(startedAt)), 0)
        ]
        if let addedKind {
            parameters[.kind] = addedKind.rawValue
        }
        Analytics.log(.onboardingCompleted, parameters: parameters)
        openChosenScreen()
        preferences.hasCompletedOnboarding = true
    }

    // Opens over Home once onboarding is gone; an invite from a link is already waiting there.
    private func openChosenScreen() {
        switch choice {
        case .invite where !navigator.hasInvite:
            navigator.request(.invite(PendingInvite()))
        case .signIn:
            navigator.request(.account)
        default:
            break
        }
    }
}
