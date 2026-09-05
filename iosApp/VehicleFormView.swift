import SwiftUI
import Shared

struct VehicleFormView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel: VehicleFormViewModel
    var onSaved: ((String, String) -> Void)? = nil
    var onDismiss: (() -> Void)? = nil

    init(
        graph: SwiftAppGraph,
        vehicleId: String?,
        onSaved: ((String, String) -> Void)? = nil,
        onDismiss: (() -> Void)? = nil
    ) {
        self.onSaved = onSaved
        self.onDismiss = onDismiss
        _viewModel = StateObject(wrappedValue: VehicleFormViewModel(graph: graph, vehicleId: vehicleId))
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField(String(localized: "vehicle_name"), text: Binding(
                        get: { viewModel.name },
                        set: { viewModel.setName($0) }
                    ))
                    .accessibilityIdentifier("vehicle_name")

                    TextField(String(localized: "initial_odometer"), text: Binding(
                        get: { viewModel.odometerText },
                        set: { viewModel.setOdometerText($0) }
                    ))
                    .keyboardType(.numberPad)
                    .disabled(!viewModel.state.canEditInitialOdometer)
                    .accessibilityIdentifier("vehicle_odometer")

                    TextField(String(localized: "vehicle_brand"), text: Binding(
                        get: { viewModel.brand },
                        set: { viewModel.setBrand($0) }
                    ))
                    .accessibilityIdentifier("vehicle_brand")

                    TextField(String(localized: "vehicle_model"), text: Binding(
                        get: { viewModel.model },
                        set: { viewModel.setModel($0) }
                    ))
                    .accessibilityIdentifier("vehicle_model")
                }

                if let message = viewModel.state.message {
                    Section {
                        Text(message.localizedText)
                            .foregroundColor(.red)
                            .font(.caption)
                            .accessibilityIdentifier("vehicle_error")
                    }
                } else if viewModel.hasOdometerError {
                    Section {
                        Text("error_out_of_range")
                            .foregroundColor(.red)
                            .font(.caption)
                            .accessibilityIdentifier("vehicle_error")
                    }
                }
            }
            .navigationTitle(viewModel.vehicleId == nil ? Text("create_vehicle_title") : Text("edit_vehicle_title"))
            .toolbar {
                // First-run creation is presented without a dismiss target, so it offers no
                // cancellation control instead of a control that cannot do anything.
                if onDismiss != nil {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("cancel") {
                            onDismiss?()
                            dismiss()
                        }
                        .accessibilityIdentifier("cancel_vehicle")
                    }
                }

                ToolbarItem(placement: .confirmationAction) {
                    Button(action: {
                        viewModel.save { outcome in
                            if let createdVehicleId = outcome.createdVehicleId {
                                onSaved?(createdVehicleId, outcome.vehicleName)
                            }
                            onDismiss?()
                            dismiss()
                        }
                    }) {
                        if viewModel.state.isSaving {
                            ProgressView()
                        } else {
                            Text("save_vehicle")
                                .fontWeight(.semibold)
                        }
                    }
                    .accessibilityIdentifier("save_vehicle")
                    .disabled(viewModel.state.isSaving || viewModel.hasOdometerError)
                }
            }
        }
    }
}
