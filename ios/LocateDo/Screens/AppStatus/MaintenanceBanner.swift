import SwiftUI

struct MaintenanceBanner: View {
    @Environment(AppStatusStore.self) private var appStatus
    @Environment(Authenticator.self) private var authenticator

    var body: some View {
        if authenticator.isSignedIn {
            content
        }
    }

    @ViewBuilder
    private var content: some View {
        switch appStatus.maintenancePhase {
        case .upcoming(let maintenance) where appStatus.showsUpcomingBanner:
            banner(maintenance, isDismissible: true) {
                Text(.maintenanceUpcoming(
                    MaintenanceText.start(of: maintenance),
                    MaintenanceText.end(of: maintenance)
                ))
            }
        case .active(let maintenance):
            banner(maintenance, isDismissible: false) {
                Text(.maintenanceActive(MaintenanceText.end(of: maintenance)))
            }
        default:
            EmptyView()
        }
    }

    private func banner(
        _ maintenance: AppStatusDocument.Maintenance,
        isDismissible: Bool,
        @ViewBuilder text: () -> some View
    ) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "wrench.and.screwdriver.fill")
                .foregroundStyle(.orange)
            VStack(alignment: .leading, spacing: 4) {
                text()
                if let message = MaintenanceText.message(of: maintenance) {
                    Text(message)
                        .foregroundStyle(.secondary)
                }
            }
            .font(.footnote)
            .frame(maxWidth: .infinity, alignment: .leading)
            if isDismissible {
                Button {
                    appStatus.dismissUpcomingBanner()
                } label: {
                    Label(.maintenanceDismiss, systemImage: "xmark")
                        .labelStyle(.iconOnly)
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(Color.orange.opacity(0.12), in: .rect(cornerRadius: 16))
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
    }
}

extension View {
    func maintenanceBanner() -> some View {
        safeAreaBar(edge: .top) {
            MaintenanceBanner()
        }
    }
}
