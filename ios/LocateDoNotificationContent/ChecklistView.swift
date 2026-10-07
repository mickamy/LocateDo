import Observation
import SwiftUI

@Observable
final class ChecklistSelection {
    var placeName = ""
    var items: [ArrivalChecklist.Item] = []
    private(set) var checked: Set<UUID> = []
    @ObservationIgnored var onChange: ([UUID]) -> Void = { _ in }

    func toggle(_ id: UUID) {
        if checked.contains(id) {
            checked.remove(id)
        } else {
            checked.insert(id)
        }
        onChange(items.map(\.id).filter(checked.contains))
    }
}

struct ChecklistView: View {
    let selection: ChecklistSelection

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(selection.placeName)
                .font(.headline)
                .padding(.horizontal)
                .padding(.vertical, 12)
            ForEach(selection.items) { item in
                row(item, isChecked: selection.checked.contains(item.id))
            }
        }
        .padding(.bottom, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func row(_ item: ArrivalChecklist.Item, isChecked: Bool) -> some View {
        Button {
            selection.toggle(item.id)
        } label: {
            HStack(spacing: 12) {
                Image(systemName: isChecked ? "checkmark.circle.fill" : "circle")
                    .font(.title3)
                    .foregroundStyle(isChecked ? Color.accentColor : Color.secondary)
                Text(item.title)
                    .strikethrough(isChecked)
                    .foregroundStyle(isChecked ? Color.secondary : Color.primary)
                Spacer(minLength: 0)
            }
            .padding(.horizontal)
            .padding(.vertical, 10)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isChecked ? .isSelected : [])
    }
}
