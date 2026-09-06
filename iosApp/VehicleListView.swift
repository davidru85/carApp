import Foundation
import SwiftUI
import Shared
import UIKit

struct VehicleDetailRoute: Hashable {
    let vehicleId: String
}

struct VehicleListView: View {
    let graph: SwiftAppGraph
    let skeletonModel: WalkingSkeletonModel
    @StateObject private var viewModel: VehicleListViewModel
    @State private var path: [VehicleDetailRoute] = []
    @State private var creation: VehicleCreationPresentation?
    @State private var firstVehicleCreationPresented = false
    @State private var previousGate: VehicleListGate = .waiting
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

    private var gate: VehicleListGate {
        vehicleListGate(isLoading: viewModel.state.isLoading, hasMessage: viewModel.state.message != nil)
    }

    var body: some View {
        content
            // The list is covered, never replaced, while it is unknown, so no navigation state is
            // torn down while it resolves. The cover and the first-run decision read the same
            // observed state, so the cover cannot outlive the decision it is waiting for. An
            // unreadable list is reported and retried instead of hidden behind an indicator.
            .overlay {
                switch gate {
                case .waiting:
                    ProgressView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(Color(UIColor.systemBackground))
                case .unreadable:
                    VStack(spacing: 16) {
                        Text("vehicle_list_unreadable")
                            .multilineTextAlignment(.center)
                            .accessibilityIdentifier("vehicle_list_unreadable")
                        Button("retry") { viewModel.refresh() }
                            .buttonStyle(.borderedProminent)
                            .accessibilityIdentifier("vehicle_list_retry")
                    }
                    .padding(24)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color(UIColor.systemBackground))
                case .resolved:
                    EmptyView()
                }
            }
            .onChange(of: gate) { newGate in
                if shouldResetOwnerScopedNavigation(gate: newGate, previousGate: previousGate) {
                    // The list no longer belongs to the session that built this navigation.
                    path = []
                    creation = nil
                    firstVehicleCreationPresented = false
                }
                previousGate = newGate
                presentFirstVehicleCreationIfNeeded()
            }
    }

    private var content: some View {
        NavigationStack(path: $path) {
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
                        NavigationLink(value: VehicleDetailRoute(vehicleId: vehicle.id)) {
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
                        creation = VehicleCreationPresentation(isFirstRun: false)
                    }) {
                        Image(systemName: "plus")
                    }
                    .accessibilityIdentifier("add_vehicle")
                }
            }
            .navigationDestination(for: VehicleDetailRoute.self) { route in
                VehicleDetailView(graph: graph, vehicleId: route.vehicleId)
            }
            .sheet(item: $creation) { presentation in
                VehicleFormView(
                    graph: graph,
                    vehicleId: nil,
                    onSaved: presentation.isFirstRun ? routeToCreatedVehicle : nil,
                    onDismiss: presentation.offersCancellation ? { creation = nil } : nil
                )
                .interactiveDismissDisabled(presentation.isMandatory)
            }
            .onAppear { presentFirstVehicleCreationIfNeeded() }
            .onChange(of: viewModel.state.vehicles.count) { _ in presentFirstVehicleCreationIfNeeded() }
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

    /// F-1 presents first-vehicle creation over the list once the list is known to be empty, so the
    /// list stays mounted underneath and the saved vehicle can be pushed onto the same stack.
    private func presentFirstVehicleCreationIfNeeded() {
        #if DEBUG
        // UI tests cannot clear the application container, and the unit-test target writes into the
        // same container, so the first-run state is not reproducible from data alone. This Debug-only
        // seam forces it, mirroring CARAPP_UI_TEST_FORCE_WELCOME. It cannot exist in a Release build.
        if ProcessInfo.processInfo.environment["CARAPP_UI_TEST_FORCE_FIRST_VEHICLE"] == "1" {
            if !firstVehicleCreationPresented {
                firstVehicleCreationPresented = true
                creation = VehicleCreationPresentation(isFirstRun: true)
            }
            return
        }
        #endif

        guard shouldPresentFirstVehicleCreation(
            isVehicleListKnown: gate == .resolved,
            vehicleCount: viewModel.state.vehicles.count,
            alreadyPresented: firstVehicleCreationPresented
        ) else {
            return
        }
        firstVehicleCreationPresented = true
        creation = VehicleCreationPresentation(isFirstRun: true)
    }

    /// `SPECIFICATION.md` F-2 routes to the created vehicle detail after saving, including the very
    /// first vehicle. Only the identifier travels: the detail titles itself from persisted state, so
    /// the canonical name that the domain produced is the one shown.
    private func routeToCreatedVehicle(vehicleId: String) {
        creation = nil
        path.append(VehicleDetailRoute(vehicleId: vehicleId))
    }
}
