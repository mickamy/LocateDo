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
                BenefitRow(
                    systemImage: "iphone.and.arrow.forward",
                    title: .settingsAccountBenefitsSyncTitle,
                    message: .settingsAccountBenefitsSyncMessage
                )
                BenefitRow(
                    systemImage: "person.2",
                    title: .settingsAccountBenefitsShareTitle,
                    message: .settingsAccountBenefitsShareMessage
                )
                BenefitRow(
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
}
