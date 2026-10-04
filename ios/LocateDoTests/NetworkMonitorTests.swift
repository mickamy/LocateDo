import Testing

@testable import LocateDo

struct NetworkMonitorTests {
    @Test func theFirstPathDoesNotCountAsAReconnect() {
        let counter = ResetCounter()
        let monitor = NetworkMonitor { counter.increment() }

        monitor.pathChanged(satisfied: true)

        #expect(counter.value == 0)
    }

    @Test func reconnectingFiresOncePerRecovery() {
        let counter = ResetCounter()
        let monitor = NetworkMonitor { counter.increment() }

        monitor.pathChanged(satisfied: false)
        monitor.pathChanged(satisfied: false)
        monitor.pathChanged(satisfied: true)
        monitor.pathChanged(satisfied: true)
        monitor.pathChanged(satisfied: false)
        monitor.pathChanged(satisfied: true)

        #expect(counter.value == 2)
    }
}
