import SwiftData
import SwiftUI

struct TodoListView: View {
    @Environment(LocalWrites.self) private var writes
    @Environment(TodoUndo.self) private var undo
    @Environment(AppRouter.self) private var router
    @Query(sort: \Place.sortOrder) private var places: [Place]
    @Query(sort: \Todo.createdAt) private var todos: [Todo]
    @State private var filter: TodoFilter = .all
    @State private var isAddingTodo = false

    private var groups: [TodoGroup] {
        TodoGrouping.groups(todos, filter: filter)
    }

    var body: some View {
        NavigationStack {
            Group {
                if places.isEmpty {
                    noPlaces
                        .syncRefreshableEmptyState()
                } else if todos.isEmpty {
                    empty
                        .syncRefreshableEmptyState()
                } else {
                    list
                        .syncRefreshable()
                }
            }
            .trackScreen(.todos)
            .navigationTitle(Text(.tabTodos))
            .maintenanceBanner()
            .toolbar {
                if !places.isEmpty {
                    ToolbarItem(placement: .primaryAction) {
                        Button {
                            isAddingTodo = true
                        } label: {
                            Label(.todoEditorTitle, systemImage: "plus")
                        }
                    }
                }
            }
            .sheet(isPresented: $isAddingTodo) {
                TodoEditorView()
            }
            .navigationDestination(for: Place.self) { place in
                PlaceDetailView(place: place)
            }
        }
    }

    private var noPlaces: some View {
        ContentUnavailableView {
            Label(.todoListNoPlacesTitle, systemImage: "mappin.and.ellipse")
        } description: {
            Text(.todoListNoPlacesMessage)
        } actions: {
            Button(.homeAddPlace) {
                router.requestAddPlace()
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
        }
    }

    private var empty: some View {
        ContentUnavailableView {
            Label(.todoListEmptyTitle, systemImage: "checklist")
        } description: {
            Text(.todoListEmptyMessage)
        } actions: {
            Button(.todoEditorTitle) {
                isAddingTodo = true
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
                        delete(group.todos, at: offsets)
                    }
                } header: {
                    NavigationLink(value: group.place) {
                        Label(group.place.name, systemImage: group.place.categoryStyle.systemImage)
                    }
                }
            }
        }
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

    private func delete(_ todos: [Todo], at offsets: IndexSet) {
        undo.offer(writes.delete(offsets.map { todos[$0] }, via: .swipe))
    }
}
