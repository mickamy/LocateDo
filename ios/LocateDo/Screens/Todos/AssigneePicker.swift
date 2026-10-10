import SwiftUI

struct AssigneePicker: View {
    let memberships: [Membership]
    @Binding var selection: UUID?

    var body: some View {
        Picker(selection: $selection) {
            Text(.todoAssigneeAnyone)
                .tag(UUID?.none)
            ForEach(memberships) { membership in
                Text(membership.shownName)
                    .tag(Optional(membership.userID))
            }
        } label: {
            Text(.todoAssigneeLabel)
        }
    }
}
