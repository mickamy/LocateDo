import CoreLocation
import SwiftUI

// Right before the system asks for location, which is when people wonder where it goes.
struct PrivacyStep: View {
    let reason: LocalizedStringResource
    let onDone: () -> Void

    @Environment(LocationProvider.self) private var locationProvider
    @State private var isRequesting = false

    var body: some View {
        OnboardingPage { isSpaced in
            if isSpaced {
                Spacer()
            }
            Image(systemName: "lock.circle.fill")
                .font(.system(size: 88))
                .foregroundStyle(.tint)
                .accessibilityHidden(true)
            Text(.onboardingPrivacyTitle)
                .font(.title2.bold())
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            VStack(alignment: .leading, spacing: 16) {
                point(.onboardingPrivacyOnDevice, systemImage: "iphone")
                point(.onboardingPrivacyNotSent, systemImage: "person.2.slash")
                point(.onboardingPrivacyNoAds, systemImage: "hand.raised")
            }
            if isSpaced {
                Spacer()
            }
            Text(reason)
                .font(.footnote)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
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
