import Shared
import SwiftUI

struct ContentView: View {
    @ObservedObject var model: WalkingSkeletonModel

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text(
                        String.localizedStringWithFormat(
                            String(localized: "session_status"),
                            model.sessionLabel
                        )
                    )
                    if model.canStartAnonymousSession {
                        Button(String(localized: "continue_without_account")) {
                            model.startAnonymousSession()
                        }
                        .disabled(model.sessionState.isBusy)
                    }
                    if model.sessionState.isBusy {
                        ProgressView()
                    }
                }

                Section(String(localized: "vehicle_section")) {
                    TextField(
                        String(localized: "vehicle_name"),
                        text: Binding(
                            get: { model.vehicleFormState.name },
                            set: model.setVehicleName
                        )
                    )
                    Button(String(localized: "save_vehicle")) {
                        model.saveVehicle()
                    }
                    .disabled(model.vehicleFormState.isSaving)

                    Button(String(localized: "restore_backup")) {
                        model.restoreBackup()
                    }
                    .disabled(model.vehicleListState.isLoading)
                }

                Section(String(localized: "saved_vehicles")) {
                    if model.vehicleListState.vehicles.isEmpty {
                        Text(String(localized: "empty_vehicles"))
                    } else {
                        ForEach(model.vehicleListState.vehicles, id: \.id) { vehicle in
                            Text(vehicle.name)
                        }
                    }
                }
            }
            .navigationTitle(String(localized: "walking_skeleton_title"))
        }
    }
}
