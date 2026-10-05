import SwiftUI

struct PaywallPlanRow: View {
    let plan: PaywallPlan
    let isSelected: Bool
    let select: () -> Void

    var body: some View {
        Button(action: select) {
            HStack(spacing: 12) {
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .font(.title3)
                    .foregroundStyle(isSelected ? Color.accentColor : Color.secondary)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 8) {
                        Text(title)
                            .font(.headline)
                        if plan.kind == .annual {
                            Text(.paywallBestValue)
                                .font(.caption.bold())
                                .padding(.horizontal, 8)
                                .padding(.vertical, 2)
                                .background(.tint, in: .capsule)
                                .foregroundStyle(.white)
                        }
                    }
                    Text(detail)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
            }
            .padding(16)
            .background(.background.secondary, in: .rect(cornerRadius: 16))
            .overlay {
                RoundedRectangle(cornerRadius: 16)
                    .stroke(isSelected ? Color.accentColor : Color.clear, lineWidth: 2)
            }
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private var title: LocalizedStringResource {
        switch plan.kind {
        case .annual: .paywallAnnual
        case .monthly: .paywallMonthly
        }
    }

    private var detail: LocalizedStringResource {
        switch plan.kind {
        case .annual:
            if let trialDays = plan.trialDays {
                return .paywallTrialThen(String(trialDays), plan.price)
            }
            return .paywallPerYear(plan.price)
        case .monthly:
            return .paywallPerMonth(plan.price)
        }
    }
}
