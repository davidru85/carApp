import XCTest
import Shared
@testable import carApp

/// `E4-03` (`D-256`): every view model is deallocated once released, and releasing a form or fuel-list
/// view model returns its holder to `SwiftAppGraph`, so neither outlives its screen.
@MainActor
final class ViewModelDeallocationTests: XCTestCase {
    private static let vehicleId = "00000000-0000-4000-8000-0000000e4031"
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

    func testVehicleListViewModelIsDeallocatedWhenReleased() {
        weak var released: VehicleListViewModel?
        do {
            let viewModel = VehicleListViewModel(graph: graph)
            released = viewModel
            XCTAssertNotNil(released)
        }
        XCTAssertNil(released)
    }

    func testVehicleFormViewModelIsDeallocatedWhenReleased() {
        weak var released: VehicleFormViewModel?
        do {
            let viewModel = VehicleFormViewModel(graph: graph, vehicleId: nil)
            released = viewModel
            XCTAssertNotNil(released)
        }
        XCTAssertNil(released)
    }

    func testFuelEntryListViewModelIsDeallocatedWhenReleased() {
        weak var released: FuelEntryListViewModel?
        do {
            let viewModel = FuelEntryListViewModel(graph: graph, vehicleId: Self.vehicleId)
            released = viewModel
            XCTAssertNotNil(released)
        }
        XCTAssertNil(released)
    }

    func testFuelEntryFormViewModelIsDeallocatedWhenReleased() {
        weak var released: FuelEntryFormViewModel?
        do {
            let viewModel = FuelEntryFormViewModel(graph: graph, vehicleId: Self.vehicleId, entryId: nil)
            released = viewModel
            XCTAssertNotNil(released)
        }
        XCTAssertNil(released)
    }

    func testSettingsViewModelIsDeallocatedWhenReleased() {
        weak var released: SettingsViewModel?
        do {
            let viewModel = SettingsViewModel(graph: graph)
            released = viewModel
            XCTAssertNotNil(released)
        }
        XCTAssertNil(released)
    }

    func testReleasingAVehicleFormViewModelReturnsItsHolderToTheGraph() {
        var viewModel: VehicleFormViewModel? = VehicleFormViewModel(graph: graph, vehicleId: nil)
        let heldHolder = graph.vehicleFormStateHolder(vehicleId: nil)
        XCTAssertNotNil(viewModel)

        viewModel = nil

        XCTAssertFalse(heldHolder === graph.vehicleFormStateHolder(vehicleId: nil))
        graph.releaseVehicleFormStateHolder(vehicleId: nil)
    }

    func testReleasingAFuelEntryListViewModelReturnsItsHolderToTheGraph() {
        var viewModel: FuelEntryListViewModel? = FuelEntryListViewModel(graph: graph, vehicleId: Self.vehicleId)
        let heldHolder = graph.fuelEntryListStateHolder(vehicleId: Self.vehicleId)
        XCTAssertNotNil(viewModel)

        viewModel = nil

        XCTAssertFalse(heldHolder === graph.fuelEntryListStateHolder(vehicleId: Self.vehicleId))
        graph.releaseFuelEntryListStateHolder(vehicleId: Self.vehicleId)
    }

    func testReleasingAFuelEntryFormViewModelReturnsItsHolderToTheGraph() {
        var viewModel: FuelEntryFormViewModel? =
            FuelEntryFormViewModel(graph: graph, vehicleId: Self.vehicleId, entryId: nil)
        let heldHolder = graph.fuelEntryFormStateHolder(vehicleId: Self.vehicleId, entryId: nil)
        XCTAssertNotNil(viewModel)

        viewModel = nil

        XCTAssertFalse(heldHolder === graph.fuelEntryFormStateHolder(vehicleId: Self.vehicleId, entryId: nil))
        graph.releaseFuelEntryFormStateHolder(vehicleId: Self.vehicleId, entryId: nil)
    }
}
