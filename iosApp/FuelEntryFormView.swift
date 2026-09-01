import SwiftUI
import Shared

struct FuelEntryFormView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel: FuelEntryFormViewModel
    var onDismiss: (() -> Void)? = nil

    init(graph: SwiftAppGraph, vehicleId: String, entryId: String?, onDismiss: (() -> Void)? = nil) {
        self.onDismiss = onDismiss
        _viewModel = StateObject(wrappedValue: FuelEntryFormViewModel(graph: graph, vehicleId: vehicleId, entryId: entryId))
    }

    private var isWarningPresented: Binding<Bool> {
        Binding(
            get: { viewModel.state.message?.confirmation != nil },
            set: { if !$0 { viewModel.clearMessage() } }
        )
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    DatePicker(
                        selection: Binding(
                            get: { viewModel.selectedDate },
                            set: { viewModel.setDate($0) }
                        ),
                        displayedComponents: .date
                    ) {
                        Text("fuel_date_value")
                    }
                    .accessibilityIdentifier("fuel_date")

                    TextField(String(localized: "current_odometer"), text: Binding(
                        get: { viewModel.odometerText },
                        set: { viewModel.setOdometerText($0) }
                    ))
                    .keyboardType(.numberPad)
                    .accessibilityIdentifier("fuel_odometer")
                }

                Section(header: Text("money_input_method")) {
                    Picker("money_input_method", selection: Binding(
                        get: { viewModel.state.moneyInputMode },
                        set: { viewModel.setMoneyInputMode($0) }
                    )) {
                        Text("money_mode_liters_price").tag(MoneyInputMode.litersAndPrice)
                            .accessibilityIdentifier("fuel_mode_liters_price")
                        Text("money_mode_liters_total").tag(MoneyInputMode.litersAndTotal)
                            .accessibilityIdentifier("fuel_mode_liters_total")
                        Text("money_mode_price_total").tag(MoneyInputMode.priceAndTotal)
                            .accessibilityIdentifier("fuel_mode_price_total")
                    }
                    .pickerStyle(.segmented)

                    switch viewModel.state.moneyInputMode {
                    case .litersAndPrice:
                        TextField(String(localized: "liters"), text: Binding(
                            get: { viewModel.litersText },
                            set: { viewModel.setLitersText($0) }
                        ))
                        .keyboardType(.decimalPad)
                        .accessibilityIdentifier("fuel_liters")

                        TextField(String(localized: "price_per_liter"), text: Binding(
                            get: { viewModel.priceText },
                            set: { viewModel.setPricePerLiterText($0) }
                        ))
                        .keyboardType(.decimalPad)
                        .accessibilityIdentifier("fuel_price_per_liter")

                        HStack {
                            Text("calculated_total")
                                .foregroundColor(.secondary)
                            Spacer()
                            if let total = viewModel.state.totalCostMinor {
                                Text("\(formatScaled(total.int64Value, scale: ScaledFormat.minorUnitScale)) \(viewModel.state.currencyCode)")
                                    .fontWeight(.medium)
                                    .accessibilityIdentifier("fuel_derived_value")
                            } else {
                                Text("value_pending")
                                    .foregroundColor(.secondary)
                                    .accessibilityIdentifier("fuel_derived_value")
                            }
                        }

                    case .litersAndTotal:
                        TextField(String(localized: "liters"), text: Binding(
                            get: { viewModel.litersText },
                            set: { viewModel.setLitersText($0) }
                        ))
                        .keyboardType(.decimalPad)
                        .accessibilityIdentifier("fuel_liters")

                        TextField(String(localized: "total_cost"), text: Binding(
                            get: { viewModel.totalText },
                            set: { viewModel.setTotalCostText($0) }
                        ))
                        .keyboardType(.decimalPad)
                        .accessibilityIdentifier("fuel_total_cost")

                        HStack {
                            Text("calculated_price")
                                .foregroundColor(.secondary)
                            Spacer()
                            if let price = viewModel.state.pricePerLiterScaled {
                                Text("\(formatScaled(price.int64Value, scale: ScaledFormat.pricePerLiterScale)) \(viewModel.state.currencyCode)")
                                    .fontWeight(.medium)
                                    .accessibilityIdentifier("fuel_derived_value")
                            } else {
                                Text("value_pending")
                                    .foregroundColor(.secondary)
                                    .accessibilityIdentifier("fuel_derived_value")
                            }
                        }

                    case .priceAndTotal:
                        TextField(String(localized: "price_per_liter"), text: Binding(
                            get: { viewModel.priceText },
                            set: { viewModel.setPricePerLiterText($0) }
                        ))
                        .keyboardType(.decimalPad)
                        .accessibilityIdentifier("fuel_price_per_liter")

                        TextField(String(localized: "total_cost"), text: Binding(
                            get: { viewModel.totalText },
                            set: { viewModel.setTotalCostText($0) }
                        ))
                        .keyboardType(.decimalPad)
                        .accessibilityIdentifier("fuel_total_cost")

                        HStack {
                            Text("calculated_liters")
                                .foregroundColor(.secondary)
                            Spacer()
                            if let liters = viewModel.state.litersScaled {
                                Text("\(formatScaled(liters.int64Value, scale: ScaledFormat.litersScale)) L")
                                    .fontWeight(.medium)
                                    .accessibilityIdentifier("fuel_derived_value")
                            } else {
                                Text("value_pending")
                                    .foregroundColor(.secondary)
                                    .accessibilityIdentifier("fuel_derived_value")
                            }
                        }
                    }
                }

                Section {
                    Toggle(String(localized: "full_tank"), isOn: Binding(
                        get: { viewModel.state.isFullTank },
                        set: { viewModel.setFullTank($0) }
                    ))
                    .accessibilityIdentifier("fuel_full_tank")

                    Toggle(String(localized: "missed_entries"), isOn: Binding(
                        get: { viewModel.state.hasMissedEntries },
                        set: { viewModel.setMissedEntries($0) }
                    ))
                    .accessibilityIdentifier("fuel_missed_entries")

                    TextField(String(localized: "notes_optional"), text: Binding(
                        get: { viewModel.notesText },
                        set: { viewModel.setNotes($0) }
                    ))
                    .accessibilityIdentifier("fuel_notes")
                }

                if let message = viewModel.state.message, message.confirmation == nil {
                    Section {
                        Text(message.localizedText)
                            .foregroundColor(.red)
                            .font(.caption)
                            .accessibilityIdentifier("fuel_error")
                    }
                }
            }
            .navigationTitle(viewModel.entryId == nil ? Text("create_fuel_entry_title") : Text("edit_fuel_entry_title"))
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("close") {
                        onDismiss?()
                        dismiss()
                    }
                    .accessibilityIdentifier("close_fuel_entry")
                }

                ToolbarItem(placement: .confirmationAction) {
                    Button(action: {
                        viewModel.save {
                            onDismiss?()
                            dismiss()
                        }
                    }) {
                        if viewModel.state.isSaving {
                            ProgressView()
                        } else {
                            Text("save_fuel_entry")
                                .fontWeight(.semibold)
                        }
                    }
                    .accessibilityIdentifier("save_fuel_entry")
                    .disabled(viewModel.state.isSaving)
                }
            }
            .alert(String(localized: "odometer_warning_title"), isPresented: isWarningPresented) {
                Button(String(localized: "save_anyway")) {
                    viewModel.confirmOdometerWarning {
                        onDismiss?()
                        dismiss()
                    }
                }
                .accessibilityIdentifier("confirm_fuel_odometer_warning")

                Button(String(localized: "cancel"), role: .cancel) {
                    viewModel.clearMessage()
                }
            } message: {
                Text("odometer_warning_body")
            }
        }
    }
}
