import Observation
import SwiftUI

@Observable
final class ChecklistSelection {
    var placeName = ""
    var systemImage = CategoryAppearance.systemImage(forIcon: nil)
    var tint = CategoryAppearance.tint(forColor: nil)
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
            header
            ForEach(selection.items) { item in
                row(item, isChecked: selection.checked.contains(item.id))
            }
        }
        .padding(.bottom, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var header: some View {
        HStack(spacing: 12) {
            Image(systemName: selection.systemImage)
                .font(.body.weight(.semibold))
                .foregroundStyle(.white)
                .frame(width: 32, height: 32)
                .background(selection.tint, in: RoundedRectangle(cornerRadius: 8))
                .accessibilityHidden(true)
            Text(selection.placeName)
                .font(.headline)
        }
        .padding(.horizontal, 20)
        .padding(.top, 20)
        .padding(.bottom, 8)
    }

    private func row(_ item: ArrivalChecklist.Item, isChecked: Bool) -> some View {
        Button {
            selection.toggle(item.id)
        } label: {
            HStack(spacing: 12) {
                Image(systemName: isChecked ? "checkmark.circle.fill" : "circle")
                    .font(.title3)
                    .foregroundStyle(isChecked ? Color.accentColor : Color.secondary)
                    .contentTransition(.symbolEffect(.replace))
                    .symbolEffect(.bounce, value: isChecked)
                Text(item.title)
                    .strikethrough(isChecked)
                    .foregroundStyle(isChecked ? Color.secondary : Color.primary)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
            // In the notification, only drawn pixels take taps; contentShape alone leaves the blank part dead.
            .background(Color.primary.opacity(0.001))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isChecked ? .isSelected : [])
    }
}
