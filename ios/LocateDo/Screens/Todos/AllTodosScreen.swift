import SwiftData
import SwiftUI

// Every to-do, grouped by place, filtered to open or done.
struct AllTodosScreen: View {
    @Environment(Navigator.self) private var navigator
    @Environment(LocalWrites.self) private var writes
    @Environment(TodoUndo.self) private var undo
    @Query(sort: \Todo.createdAt) private var todos: [Todo]
    @State private var filter: TodoFilter = .open
    let entry: ScreenEntry

    private var groups: [TodoGroup] {
        TodoGrouping.groups(todos, filter: filter)
    }

    private var completed: [Todo] {
        todos.filter(\.isCompleted)
    }

    var body: some View {
        Group {
            if todos.isEmpty {
                empty
                    .syncRefreshableEmptyState()
            } else {
                list
                    .syncRefreshable()
            }
        }
        .trackScreen(.todos, opening: entry.parameters)
        .navigationTitle(Text(.homeAllTodos))
        .navigationBarTitleDisplayMode(.inline)
        .maintenanceBanner()
    }

    private var empty: some View {
        ContentUnavailableView {
            Label(.todoListEmptyTitle, systemImage: "checklist")
        } description: {
            Text(.todoListEmptyMessage)
        } actions: {
            Button(.todoEditorTitle) {
                navigator.present(.addTodo(place: nil, entry: .allTodosEmpty))
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
        }
    }

    private var list: some View {
        List {
            Section {
                filterPicker
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)
            } footer: {
                if filter == .done && !completed.isEmpty {
                    HStack {
                        Spacer()
                        DeleteCompletedButton(todos: completed)
                    }
                }
            }
            if groups.isEmpty {
                Text(.todoListFilterEmpty)
                    .foregroundStyle(.secondary)
            }
            ForEach(groups) { group in
                Section {
                    ForEach(group.todos) { todo in
                        TodoRow(todo: todo)
                    }
                    .onDelete { offsets in
                        undo.offer(writes.delete(offsets.map { group.todos[$0] }, via: .swipe))
                    }
                } header: {
                    NavigationLink(value: Route.place(group.place, entry: .allTodos)) {
                        Label(group.place.name, systemImage: group.place.categoryStyle.systemImage)
                    }
                }
            }
        }
        .contentMargins(.top, 8, for: .scrollContent)
    }

    private var filterPicker: some View {
        Picker(selection: $filter) {
            ForEach(TodoFilter.allCases) { filter in
                Text(filter.title)
                    .tag(filter)
            }
        } label: {
            Text(.tabTodos)
        }
        .pickerStyle(.segmented)
        .labelsHidden()
    }
}
