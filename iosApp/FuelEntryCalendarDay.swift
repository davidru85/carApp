import Foundation

final class FuelEntryCalendarDay {
    let timeZone: TimeZone
    let locale: Locale

    init(timeZone: TimeZone = .current, locale: Locale = .current) {
        self.timeZone = timeZone
        self.locale = locale
    }

    func format(epochMillis: Int64) -> String {
        return ""
    }

    func startOfDay(for epochMillis: Int64) -> Int64 {
        return 0
    }

    func startOfDay(for date: Date) -> Int64 {
        return 0
    }

    func date(from epochMillis: Int64) -> Date {
        return Date(timeIntervalSince1970: 0)
    }
}
