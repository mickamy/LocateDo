import Foundation

enum MaintenanceText {
    static func start(of maintenance: AppStatusDocument.Maintenance, calendar: Calendar = .current) -> String {
        maintenance.startsAt.formatted(dateTimeStyle(calendar: calendar))
    }

    static func end(of maintenance: AppStatusDocument.Maintenance, calendar: Calendar = .current) -> String {
        if calendar.isDate(maintenance.startsAt, inSameDayAs: maintenance.endsAt) {
            return maintenance.endsAt.formatted(timeStyle(calendar: calendar))
        }
        return maintenance.endsAt.formatted(dateTimeStyle(calendar: calendar))
    }

    static func message(of maintenance: AppStatusDocument.Maintenance) -> String? {
        maintenance.message?.text(for: Bundle.main.preferredLocalizations.first)
    }

    private static func dateTimeStyle(calendar: Calendar) -> Date.FormatStyle {
        var style = Date.FormatStyle(date: .abbreviated, time: .shortened)
        style.calendar = calendar
        style.timeZone = calendar.timeZone
        return style
    }

    private static func timeStyle(calendar: Calendar) -> Date.FormatStyle {
        var style = Date.FormatStyle(date: .omitted, time: .shortened)
        style.calendar = calendar
        style.timeZone = calendar.timeZone
        return style
    }
}
