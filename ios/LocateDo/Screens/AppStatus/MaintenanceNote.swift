import SwiftUI

struct MaintenanceNote: View {
    @Environment(AppStatusStore.self) private var appStatus

    var body: some View {
        if let maintenance = appStatus.activeMaintenance {
            Text(.maintenanceUnavailable(MaintenanceText.end(of: maintenance)))
                .font(.footnote)
                .foregroundStyle(.orange)
        }
    }
}
