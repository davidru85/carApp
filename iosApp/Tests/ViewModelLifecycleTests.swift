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

    func testVehicleListRequestDeleteEmitsConfirmationMessageWithoutDeleting() async throws {
        let localGraph = createSwiftAppGraph(isDebugBuild: false)
        defer { localGraph.close() }

        let vModel = VehicleFormViewModel(graph: localGraph, vehicleId: nil)
        vModel.setName("DeleteMe-\(UUID().uuidString.prefix(8))")
        vModel.setOdometerText("30000")

        var saved = false
        vModel.save { saved = true }
        for _ in 0..<30 {
            if saved { break }
            try await Task.sleep(nanoseconds: 100_000_000)
        }
        XCTAssertTrue(saved, "Vehicle should save so it can be deleted")
        let vehicleId = try XCTUnwrap(vModel.state.savedVehicleId)

        let list = VehicleListViewModel(graph: localGraph)
        list.refresh()
        for _ in 0..<30 {
            if !list.state.vehicles.isEmpty { break }
            try await Task.sleep(nanoseconds: 100_000_000)
        }
        let beforeCount = list.state.vehicles.count
        XCTAssertGreaterThan(beforeCount, 0)

        list.requestDelete(vehicleId)

        for _ in 0..<30 {
            if list.state.message != nil { break }
            try await Task.sleep(nanoseconds: 100_000_000)
        }
        let message = try XCTUnwrap(list.state.message, "requestDelete should emit a confirmation message")
        XCTAssertEqual(message.code, "INFO.CONFIRM_DELETE_VEHICLE")
        XCTAssertEqual(list.state.selectedVehicleId, vehicleId)

        for _ in 0..<10 {
            try await Task.sleep(nanoseconds: 100_000_000)
        }
        XCTAssertEqual(list.state.vehicles.count, beforeCount, "requestDelete must not delete the vehicle until confirmDelete is called")
    }

    func testVehicleListConfirmDeleteAfterRequestDeletesVehicle() async throws {
        let localGraph = createSwiftAppGraph(isDebugBuild: false)
        defer { localGraph.close() }

        let vModel = VehicleFormViewModel(graph: localGraph, vehicleId: nil)
        vModel.setName("ConfirmMe-\(UUID().uuidString.prefix(8))")
        vModel.setOdometerText("20000")

        var saved = false
        vModel.save { saved = true }
        for _ in 0..<30 {
            if saved { break }
            try await Task.sleep(nanoseconds: 100_000_000)
        }
        let vehicleId = try XCTUnwrap(vModel.state.savedVehicleId)

        let list = VehicleListViewModel(graph: localGraph)
        list.refresh()
        for _ in 0..<30 {
            if list.state.vehicles.contains(where: { $0.id == vehicleId }) { break }
            try await Task.sleep(nanoseconds: 100_000_000)
        }
        XCTAssertTrue(list.state.vehicles.contains(where: { $0.id == vehicleId }))

        list.requestDelete(vehicleId)
        for _ in 0..<30 {
            if list.state.message != nil { break }
            try await Task.sleep(nanoseconds: 100_000_000)
        }
        XCTAssertNotNil(list.state.message)

        list.confirmDelete(vehicleId)
        for _ in 0..<30 {
            if !list.state.vehicles.contains(where: { $0.id == vehicleId }) { break }
            try await Task.sleep(nanoseconds: 100_000_000)
        }
        XCTAssertFalse(list.state.vehicles.contains(where: { $0.id == vehicleId }), "confirmDelete should remove the vehicle")
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
