import Foundation

final class FuelEntryCalendarDay {
    let timeZone: TimeZone
    let locale: Locale
    private let calendar: Calendar
    private let dateFormatter: DateFormatter

    init(timeZone: TimeZone = .current, locale: Locale = .current) {
        self.timeZone = timeZone
        self.locale = locale
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = timeZone
        cal.locale = locale
        self.calendar = cal

        let formatter = DateFormatter()
        formatter.timeZone = timeZone
        formatter.locale = locale
        formatter.dateStyle = .medium
        formatter.timeStyle = .none
        self.dateFormatter = formatter
    }

    func format(epochMillis: Int64) -> String {
        let d = date(from: epochMillis)
        return dateFormatter.string(from: d)
    }

    func startOfDay(for epochMillis: Int64) -> Int64 {
        let d = date(from: epochMillis)
        let start = calendar.startOfDay(for: d)
        return epochMillisFromDate(start)
    }

    func startOfDay(for date: Date) -> Int64 {
        let start = calendar.startOfDay(for: date)
        return epochMillisFromDate(start)
    }

    func date(from epochMillis: Int64) -> Date {
        Date(timeIntervalSince1970: Double(epochMillis) / 1000.0)
    }

    func atStartOfDay(year: Int, month: Int, day: Int) -> Int64 {
        var components = DateComponents()
        components.year = year
        components.month = month
        components.day = day
        components.hour = 0
        components.minute = 0
        components.second = 0
        guard let d = calendar.date(from: components) else { return 0 }
        let start = calendar.startOfDay(for: d)
        return epochMillisFromDate(start)
    }

    private func epochMillisFromDate(_ date: Date) -> Int64 {
        Int64(date.timeIntervalSince1970 * 1000.0)
    }
}
