import SwiftUI
import Shared

struct VehicleDetailRoute: Hashable {
    let vehicleId: String
    let vehicleName: String
}

struct VehicleListView: View {
    let graph: SwiftAppGraph
    let skeletonModel: WalkingSkeletonModel
    @StateObject private var viewModel: VehicleListViewModel
    @State private var isCreatingVehicle = false
    @State private var pendingDeleteVehicleId: String?

    init(graph: SwiftAppGraph, skeletonModel: WalkingSkeletonModel) {
        self.graph = graph
        self.skeletonModel = skeletonModel
        _viewModel = StateObject(wrappedValue: VehicleListViewModel(graph: graph))
    }

    private var isDeleteConfirmationPresented: Binding<Bool> {
        Binding(
            get: { pendingDeleteVehicleId != nil },
            set: { if !$0 { pendingDeleteVehicleId = nil } }
        )
    }

    var body: some View {
        NavigationStack {
            List {
                if viewModel.state.vehicles.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "car.2.fill")
                            .font(.system(size: 48))
                            .foregroundColor(.secondary)
                        Text("empty_vehicles")
                            .font(.headline)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 40)
                    .listRowBackground(Color.clear)
                } else {
                    ForEach(viewModel.state.vehicles, id: \.id) { vehicle in
                        NavigationLink(value: VehicleDetailRoute(vehicleId: vehicle.id, vehicleName: vehicle.name)) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(vehicle.name)
                                    .font(.headline)

                                Text(String(format: String(localized: "vehicle_odometer"), vehicle.currentOdometerKm))
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                            .padding(.vertical, 4)
                        }
                        .accessibilityIdentifier("vehicle_row_\(vehicle.id)")
                        .swipeActions(edge: .trailing) {
                            Button(role: .destructive) {
                                pendingDeleteVehicleId = vehicle.id
                                viewModel.requestDelete(vehicle.id)
                            } label: {
                                Label("delete", systemImage: "trash")
                            }
                        }
                    }
                }
            }
            .navigationTitle(Text("vehicle_list_title"))
            .toolbar {
                #if DEBUG
                ToolbarItem(placement: .navigationBarLeading) {
                    NavigationLink(destination: DiagnosticsView(model: skeletonModel)) {
                        Image(systemName: "wrench.and.screwdriver")
                    }
                    .accessibilityIdentifier("diagnostics_button")
                }
                #endif

                ToolbarItem(placement: .primaryAction) {
                    Button(action: {
                        isCreatingVehicle = true
                    }) {
                        Image(systemName: "plus")
                    }
                    .accessibilityIdentifier("add_vehicle")
                }
            }
            .navigationDestination(for: VehicleDetailRoute.self) { route in
                VehicleDetailView(graph: graph, vehicleId: route.vehicleId, vehicleName: route.vehicleName)
            }
            .sheet(isPresented: $isCreatingVehicle) {
                VehicleFormView(graph: graph, vehicleId: nil) {
                    isCreatingVehicle = false
                }
            }
            .alert(String(localized: "delete_vehicle_title"), isPresented: isDeleteConfirmationPresented) {
                Button(String(localized: "delete"), role: .destructive) {
                    if let id = pendingDeleteVehicleId {
                        viewModel.confirmDelete(id)
                    }
                    pendingDeleteVehicleId = nil
                }
                .accessibilityIdentifier("confirm_delete_vehicle")
                Button(String(localized: "cancel"), role: .cancel) {
                    viewModel.clearMessage()
                    pendingDeleteVehicleId = nil
                }
            } message: {
                Text("delete_vehicle_confirmation")
            }
        }
    }
}
