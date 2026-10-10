import CoreLocation
import SwiftUI

// Right before the system asks for location, which is when people wonder where it goes.
struct PrivacyStep: View {
    let reason: LocalizedStringResource
    let onDone: () -> Void

    @Environment(LocationProvider.self) private var locationProvider
    @State private var isRequesting = false

    var body: some View {
        VStack(spacing: 20) {
            Spacer()
            Image(systemName: "lock.circle.fill")
                .font(.system(size: 88))
                .foregroundStyle(.tint)
                .accessibilityHidden(true)
            Text(.onboardingPrivacyTitle)
                .font(.title2.bold())
                .multilineTextAlignment(.center)
            VStack(alignment: .leading, spacing: 16) {
                point(.onboardingPrivacyOnDevice, systemImage: "iphone")
                point(.onboardingPrivacyNotSent, systemImage: "person.2.slash")
                point(.onboardingPrivacyNoAds, systemImage: "hand.raised")
            }
            Spacer()
            Text(reason)
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
        .padding(32)
        .onAppear {
            Analytics.logScreen(.onboarding, parameters: [.step: "privacy"])
        }
        .onChange(of: locationProvider.authorizationStatus) {
            if isRequesting, locationProvider.authorizationStatus != .notDetermined {
                isRequesting = false
                onDone()
            }
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

    private func requestLocation() {
        isRequesting = true
        locationProvider.start()
        if locationProvider.authorizationStatus != .notDetermined {
            isRequesting = false
            onDone()
        }
    }
}
