import SwiftUI

struct AnalyticsConsentStep: View {
    @Environment(AnalyticsConsent.self) private var analyticsConsent

    var body: some View {
        Image(systemName: "chart.bar.xaxis")
            .font(.system(size: 72))
            .foregroundStyle(.tint)
            .accessibilityHidden(true)
        Text(.onboardingAnalyticsTitle)
            .font(.title2.bold())
            .multilineTextAlignment(.center)
        VStack(alignment: .leading, spacing: 16) {
            point(.onboardingAnalyticsMessage, systemImage: "chart.line.uptrend.xyaxis")
            point(.onboardingAnalyticsNotSent, systemImage: "lock")
            point(.onboardingAnalyticsSettings, systemImage: "gearshape")
        }
        Link(destination: LegalLinks.privacyPolicy) {
            Text(.settingsAboutPrivacyPolicy)
        }
        .font(.footnote)
        Spacer()
        // Equal weight, so declining is as easy as agreeing.
        VStack(spacing: 12) {
            answerButton(.onboardingAnalyticsAllow, isOn: true)
                .accessibilityIdentifier("onboarding.analyticsAllow")
            answerButton(.onboardingAnalyticsDeny, isOn: false)
                .accessibilityIdentifier("onboarding.analyticsDeny")
        }
    }

    private func point(_ text: LocalizedStringResource, systemImage: String) -> some View {
        Label {
            Text(text)
                .fixedSize(horizontal: false, vertical: true)
        } icon: {
            Image(systemName: systemImage)
                .foregroundStyle(.tint)
                .frame(width: 28)
        }
    }

    private func answerButton(_ title: LocalizedStringResource, isOn: Bool) -> some View {
        Button {
            analyticsConsent.set(isOn, source: .onboarding)
        } label: {
            Text(title)
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.bordered)
        .controlSize(.large)
    }
}
