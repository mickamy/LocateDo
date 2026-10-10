import Foundation
import SwiftData
import Testing

@testable import LocateDo

struct EntryAnalyticsTests {
    @Test func aTodoFromTheEditorSaysWhereItStartedAndWhetherThePlaceWasKept() throws {
        let fixture = try Fixture()
        let place = fixture.place()
        let origin = TodoAddOrigin(entry: .homeMenu, placePreset: .nearest, placeChanged: true)

        fixture.writes.add(Todo(title: "Milk", place: place, placeEvent: .departure), origin: origin)

        let values = try fixture.recorder.values(of: .todoAdded)
        #expect(values["entry"] as? String == "home_menu")
        #expect(values["place_preset"] as? String == "nearest")
        #expect(values["place_changed"] as? Int == 1)
        #expect(values["place_event"] as? String == "departure")
    }

    @Test func aTodoTypedWithANewPlaceHasNoEditorOrigin() throws {
        let fixture = try Fixture()

        fixture.writes.add(Place(name: "Grocery", latitude: 35.0, longitude: 139.0), todoTitles: ["Milk"])

        let values = try fixture.recorder.values(of: .todoAdded)
        #expect(values["entry"] == nil)
        #expect(values["place_preset"] == nil)
        #expect(values["place_event"] as? String == "arrival")
    }

    @Test func aPlaceSaysWhereItsFlowStarted() throws {
        let fixture = try Fixture()

        fixture.writes.add(Place(name: "Grocery", latitude: 35.0, longitude: 139.0), entry: .todoEditor)

        #expect(try fixture.recorder.values(of: .placeAdded)["entry"] as? String == "todo_editor")
    }

    @Test func switchingWhenATodoRemindsIsLoggedWithTheNewKind() throws {
        let fixture = try Fixture()
        let place = fixture.place()
        let todo = Todo(title: "Milk", place: place)
        fixture.writes.add(todo)

        fixture.writes.update(todo, title: "Milk", place: place, assigneeID: nil, placeEvent: .arrival)
        fixture.writes.update(todo, title: "Milk", place: place, assigneeID: nil, placeEvent: .departure)

        #expect(fixture.recorder.names.filter { $0 == .todoTriggerChanged }.count == 1)
        #expect(try fixture.recorder.values(of: .todoTriggerChanged)["place_event"] as? String == "departure")
    }

    @Test func completionsFromAReminderSayWhichKind() throws {
        let fixture = try Fixture()
        let place = fixture.place()
        let milk = Todo(title: "Milk", place: place, placeEvent: .departure)
        let eggs = Todo(title: "Eggs", place: place)
        fixture.writes.add(milk)
        fixture.writes.add(eggs)

        fixture.writes.checkOff([milk.id])
        fixture.writes.toggleCompletion(eggs)

        let completions = fixture.recorder.events.filter { $0.name == .todoCompleted }
        #expect(completions.map { $0.values["via"] as? String } == ["action", "app"])
        #expect(completions.map { $0.values["place_event"] as? String } == ["departure", nil])
    }

    private struct Fixture {
        let container: ModelContainer
        let writes: LocalWrites
        let recorder = RecordingAnalytics()

        init() throws {
            container = try AppModelContainer.make(inMemory: true)
            writes = LocalWrites(context: container.mainContext) { false }
            writes.analytics = recorder
        }

        func place() -> Place {
            let place = Place(name: "Grocery", latitude: 35.0, longitude: 139.0)
            writes.add(place)
            return place
        }
    }
}
