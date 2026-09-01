import XCTest
import Shared
@testable import carApp

@MainActor
final class ViewModelLifecycleTests: XCTestCase {
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

    func testVehicleListViewModelInitialState() {
        let viewModel = VehicleListViewModel(graph: graph)
        XCTAssertNotNil(viewModel.state)
        viewModel.refresh()
    }

    func testVehicleFormViewModelEditingAndValidation() {
        let viewModel = VehicleFormViewModel(graph: graph, vehicleId: nil)
        viewModel.setName("Golf")
        XCTAssertEqual(viewModel.name, "Golf")

        viewModel.setOdometerText("142500")
        XCTAssertEqual(viewModel.odometerText, "142500")
        XCTAssertFalse(viewModel.hasOdometerError)

        viewModel.setOdometerText("9999999999")
        XCTAssertTrue(viewModel.hasOdometerError)
    }

    func testVehicleFormViewModelSave() async throws {
        let viewModel = VehicleFormViewModel(graph: graph, vehicleId: nil)
        viewModel.setName("Focus-\(UUID().uuidString.prefix(8))")
        viewModel.setOdometerText("50000")
        
        var saved = false
        viewModel.save {
            saved = true
        }

        for _ in 0..<30 {
            if saved { break }
            try await Task.sleep(nanoseconds: 100_000_000)
        }
        XCTAssertTrue(saved, "Vehicle save should complete, message: \(String(describing: viewModel.state.message?.code))")
    }

    func testFuelEntryFormViewModelModeDerivations() {
        let viewModel = FuelEntryFormViewModel(graph: graph, vehicleId: "test-v-1", entryId: nil)

        viewModel.setMoneyInputMode(.litersAndPrice)
        viewModel.setLitersText("45.200")
        viewModel.setPricePerLiterText("1.629")

        XCTAssertEqual(viewModel.litersText, "45.200")
        XCTAssertEqual(viewModel.priceText, "1.629")
    }

    func testFuelEntryFormViewModelSave() async throws {
        let vModel = VehicleFormViewModel(graph: graph, vehicleId: nil)
        vModel.setName("Polo-\(UUID().uuidString.prefix(8))")
        vModel.setOdometerText("10000")
        
        var vehicleSaved = false
        vModel.save {
            vehicleSaved = true
        }

        for _ in 0..<30 {
            if vehicleSaved { break }
            try await Task.sleep(nanoseconds: 100_000_000)
        }
        XCTAssertTrue(vehicleSaved, "Vehicle should save successfully")
        let vehicleId = try XCTUnwrap(vModel.state.savedVehicleId)

        let fModel = FuelEntryFormViewModel(graph: graph, vehicleId: vehicleId, entryId: nil)
        fModel.setOdometerText("10500")
        fModel.setLitersText("40.000")
        fModel.setPricePerLiterText("1.500")
        
        var fuelSaved = false
        fModel.save {
            fuelSaved = true
        }

        for _ in 0..<30 {
            if fuelSaved { break }
            try await Task.sleep(nanoseconds: 100_000_000)
        }
        XCTAssertTrue(fuelSaved, "Fuel entry should save successfully")
    }
}
