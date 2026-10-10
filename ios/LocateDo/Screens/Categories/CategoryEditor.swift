import SwiftUI

struct CategoryEditor: View {
    static let maxNameLength = 50

    let category: PlaceCategory?

    @Environment(LocalWrites.self) private var writes
    @Environment(\.dismiss) private var dismiss

    @State private var name: String
    @State private var icon: String
    @State private var color: String

    init(category: PlaceCategory?) {
        self.category = category
        _name = State(initialValue: category?.displayName ?? "")
        _icon = State(initialValue: category?.icon ?? "mappin")
        _color = State(initialValue: category?.color ?? "blue")
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    HStack(spacing: 12) {
                        Image(systemName: CategoryAppearance.systemImage(forIcon: icon))
                            .font(.title3)
                            .foregroundStyle(.white)
                            .frame(width: 44, height: 44)
                            .background(CategoryAppearance.tint(forColor: color), in: .circle)
                            .accessibilityHidden(true)
                        TextField(text: $name) {
                            Text(.categoryNamePlaceholder)
                        }
                        .onChange(of: name) {
                            if name.count > Self.maxNameLength {
                                name = String(name.prefix(Self.maxNameLength))
                            }
                        }
                    }
                } header: {
                    Text(.categoryName)
                }
                Section {
                    LazyVGrid(columns: Self.columns, spacing: 12) {
                        ForEach(CategoryPalette.icons, id: \.self) { key in
                            iconButton(key)
                        }
                    }
                    .padding(.vertical, 4)
                } header: {
                    Text(.categoryIcon)
                }
                Section {
                    LazyVGrid(columns: Self.columns, spacing: 12) {
                        ForEach(CategoryPalette.colors, id: \.self) { key in
                            colorButton(key)
                        }
                    }
                    .padding(.vertical, 4)
                } header: {
                    Text(.categoryColor)
                }
            }
            .trackScreen(.categoryEditor, parameters: [.mode: EditorMode(editing: category).rawValue])
            .navigationTitle(Text(category == nil ? .categoryNewTitle : .categoryEditTitle))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(.commonCancel) {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(.commonSave) {
                        save()
                    }
                    .disabled(trimmedName.isEmpty)
                }
            }
        }
    }

    private static let columns = [GridItem(.adaptive(minimum: 44), spacing: 12)]

    private var trimmedName: String {
        name.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func iconButton(_ key: String) -> some View {
        let isSelected = key == icon
        let tint = CategoryAppearance.tint(forColor: color)
        return Button {
            icon = key
        } label: {
            Image(systemName: CategoryAppearance.systemImage(forIcon: key))
                .font(.title3)
                .frame(width: 44, height: 44)
                .foregroundStyle(isSelected ? Color.white : tint)
                .background(isSelected ? tint : Color(.tertiarySystemFill), in: .circle)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private func colorButton(_ key: String) -> some View {
        let isSelected = key == color
        return Button {
            color = key
        } label: {
            Circle()
                .fill(CategoryAppearance.tint(forColor: key))
                .frame(width: 32, height: 32)
                .padding(4)
                .overlay {
                    if isSelected {
                        Circle()
                            .strokeBorder(CategoryAppearance.tint(forColor: key), lineWidth: 2)
                    }
                }
                .frame(width: 44, height: 44)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(CategoryPalette.colorName(key)))
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private func save() {
        guard let category else {
            writes.add(PlaceCategory(name: trimmedName, icon: icon, color: color, sortOrder: 0))
            dismiss()
            return
        }
        category.name = Self.storedName(trimmedName, for: category.builtin)
        category.icon = icon
        category.color = color
        writes.update(category)
        dismiss()
    }

    // A built-in keeps no name while it shows its translated title, so it follows the device language.
    static func storedName(_ name: String, for builtin: BuiltinCategory?) -> String? {
        if let builtin, name == String(localized: builtin.title) {
            return nil
        }
        return name
    }
}
