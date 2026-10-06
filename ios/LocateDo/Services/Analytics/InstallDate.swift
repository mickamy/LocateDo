import Foundation

// Installs from before this existed start counting at the first launch of the version that added it.
nonisolated enum InstallDate {
    static let key = "firstLaunchedAt"

    static func record(defaults: UserDefaults, now: Date) {
        if defaults.object(forKey: key) != nil {
            return
        }
        defaults.set(now, forKey: key)
    }

    static func daysSinceInstall(defaults: UserDefaults, now: Date, calendar: Calendar = .current) -> Int {
        guard let installedAt = defaults.object(forKey: key) as? Date else {
            return 0
        }
        let start = calendar.startOfDay(for: installedAt)
        let today = calendar.startOfDay(for: now)
        let days = calendar.dateComponents([.day], from: start, to: today).day ?? 0
        return max(days, 0)
    }
}
