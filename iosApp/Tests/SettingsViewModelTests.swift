import Foundation
import Shared
import XCTest
@testable import carApp

@MainActor
final class SettingsViewModelTests: XCTestCase {
    private var graph: SwiftAppGraph!

    override func setUp() {
        super.setUp()
        graph = createSwiftAppGraph(isDebugBuild: false)
    }

    override func tearDown() {
        graph.close()
        graph = nil
        super.tearDown()
    }

    /// `docs/CONTRACTS.md §20.0.1`: the picker offers `SUPPORTED_CURRENCY_CODES` in contract order.
    func testTheSupportedCurrenciesArriveInContractOrder() async throws {
        let viewModel = SettingsViewModel(graph: graph)
        try await waitForStoredSettings(viewModel)

        XCTAssertEqual(
            viewModel.state.supportedCurrencyCodes,
            ["ARS", "AUD", "BRL", "CAD", "CHF", "COP", "CZK", "DKK", "EUR", "GBP", "HUF",
             "MAD", "MXN", "NOK", "NZD", "PEN", "PLN", "RON", "SEK", "USD", "UYU"]
        )
    }

    func testTheStoredCurrencyIsOneOfTheSupportedCodes() async throws {
        let viewModel = SettingsViewModel(graph: graph)
        try await waitForStoredSettings(viewModel)

        let code = try XCTUnwrap(viewModel.state.currencyCode)
        XCTAssertTrue(viewModel.state.supportedCurrencyCodes.contains(code))
    }

    /// `§3.1`: the MVP units are fixed at kilometres and litres.
    func testTheUnitsArePublishedAsKilometresAndLitres() async throws {
        let viewModel = SettingsViewModel(graph: graph)
        try await waitForStoredSettings(viewModel)

        XCTAssertEqual(viewModel.state.distanceUnit, .km)
        XCTAssertEqual(viewModel.state.volumeUnit, .liter)
    }

    private func waitForStoredSettings(_ viewModel: SettingsViewModel) async throws {
        for _ in 0..<30 {
            if viewModel.state.currencyCode != nil { return }
            try await Task.sleep(nanoseconds: 100_000_000)
        }
        XCTFail("The settings holder never published the stored settings")
    }
}
