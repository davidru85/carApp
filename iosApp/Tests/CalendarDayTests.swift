import XCTest
@testable import carApp

final class CalendarDayTests: XCTestCase {
    func testFormattingInSpecificTimeZone() {
        guard let madridZone = TimeZone(identifier: "Europe/Madrid") else {
            XCTFail("Missing Europe/Madrid timezone")
            return
        }
        let locale = Locale(identifier: "es_ES")
        let calendarDay = FuelEntryCalendarDay(timeZone: madridZone, locale: locale)

        // 2026-10-15 10:30:00 UTC = 12:30:00 CEST
        let epochMillis: Int64 = 1792060200000
        let formatted = calendarDay.format(epochMillis: epochMillis)
        XCTAssertFalse(formatted.isEmpty)
        XCTAssertTrue(formatted.contains("15") || formatted.contains("oct"))
    }

    func testStartOfDayInTimeZone() {
        guard let madridZone = TimeZone(identifier: "Europe/Madrid") else {
            XCTFail("Missing Europe/Madrid timezone")
            return
        }
        let calendarDay = FuelEntryCalendarDay(timeZone: madridZone, locale: Locale(identifier: "es_ES"))

        // 2026-10-15 23:30:00 UTC -> In Madrid (+02:00 DST), this is 2026-10-16 01:30:00!
        let epochMillis: Int64 = 1792107000000
        let startOfDayMillis = calendarDay.startOfDay(for: epochMillis)

        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = madridZone
        let startOfDayDate = Date(timeIntervalSince1970: Double(startOfDayMillis) / 1000.0)
        let components = calendar.dateComponents([.year, .month, .day, .hour, .minute, .second], from: startOfDayDate)

        XCTAssertEqual(components.year, 2026)
        XCTAssertEqual(components.month, 10)
        XCTAssertEqual(components.day, 16)
        XCTAssertEqual(components.hour, 0)
        XCTAssertEqual(components.minute, 0)
        XCTAssertEqual(components.second, 0)
    }
}
