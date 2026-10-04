import SwiftUI

struct AccountBenefitsView: View {
    var body: some View {
        VStack(spacing: 32) {
            VStack(spacing: 12) {
                Image(systemName: "person.crop.circle")
                    .font(.system(size: 64))
                    .foregroundStyle(.tint)
                    .accessibilityHidden(true)
                Text(.settingsAccountDescription)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            VStack(alignment: .leading, spacing: 24) {
                benefit(
                    systemImage: "iphone.and.arrow.forward",
                    title: .settingsAccountBenefitsSyncTitle,
                    message: .settingsAccountBenefitsSyncMessage
                )
                benefit(
                    systemImage: "person.2",
                    title: .settingsAccountBenefitsShareTitle,
                    message: .settingsAccountBenefitsShareMessage
                )
                benefit(
                    systemImage: "lock.shield",
                    title: .settingsAccountBenefitsPrivacyTitle,
                    message: .settingsAccountBenefitsPrivacyMessage
                )
            }
        }
        .padding(.horizontal, 32)
        .padding(.vertical, 40)
        .frame(maxWidth: .infinity)
    }

    private func benefit(
        systemImage: String,
        title: LocalizedStringResource,
        message: LocalizedStringResource
    ) -> some View {
        HStack(alignment: .top, spacing: 16) {
            Image(systemName: systemImage)
                .font(.title2)
                .foregroundStyle(.tint)
                .frame(width: 32)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.headline)
                Text(message)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .accessibilityElement(children: .combine)
    }
}
