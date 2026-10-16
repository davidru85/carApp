import SwiftUI
import Shared

struct FuelEntryFormView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
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
                        // `E4-02`: `fuel_date_value` is "Date: %@"; it now receives the formatted day.
                        Text(String(
                            format: String(localized: "fuel_date_value"),
                            viewModel.calendarDay.format(epochMillis: viewModel.state.dateEpochMillis)
                        ))
                    }
                    .accessibilityIdentifier("fuel_date")

                    TextField(String(localized: "current_odometer"), text: Binding(
                        get: { viewModel.odometerText },
                        set: { viewModel.setOdometerText($0) }
                    ))
                    .keyboardType(.numberPad)
                    .accessibilityIdentifier("fuel_odometer")
                }

                Section(header: Text("money_input_method").foregroundStyle(.primary)) {
                    moneyModePicker

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

                        DerivedValueRow(
                            labelKey: "calculated_total",
                            value: viewModel.state.totalCostMinor.map { total in
                                formatCurrencyAmount(
                                    total.int64Value,
                                    scale: ScaledFormat.minorUnitScale,
                                    currencyCode: viewModel.state.currencyCode,
                                    locale: .current
                                )
                            }
                        )

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

                        DerivedValueRow(
                            labelKey: "calculated_price",
                            value: viewModel.state.pricePerLiterScaled.map { price in
                                formatCurrencyAmount(
                                    price.int64Value,
                                    scale: ScaledFormat.pricePerLiterScale,
                                    currencyCode: viewModel.state.currencyCode,
                                    locale: .current
                                )
                            }
                        )

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

                        DerivedValueRow(
                            labelKey: "calculated_liters",
                            value: viewModel.state.litersScaled.map { liters in
                                String(
                                    format: String(localized: "liters_value"),
                                    formatScaledDecimal(liters.int64Value, scale: ScaledFormat.litersScale, locale: .current)
                                )
                            }
                        )
                    }
                }

                Section {
                    // `E4-02`: each toggle carries its help text, as on Android, so VoiceOver reads the
                    // consequence of switching it.
                    Toggle(isOn: Binding(
                        get: { viewModel.state.isFullTank },
                        set: { viewModel.setFullTank($0) }
                    )) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("full_tank")
                            Text("full_tank_help")
                                .font(.footnote)
                        }
                    }
                    .accessibilityIdentifier("fuel_full_tank")

                    Toggle(isOn: Binding(
                        get: { viewModel.state.hasMissedEntries },
                        set: { viewModel.setMissedEntries($0) }
                    )) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("missed_entries")
                            Text("missed_entries_help")
                                .font(.footnote)
                        }
                    }
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
                            .foregroundStyle(AccessibleColors.errorText)
                            .font(.footnote)
                            .fixedSize(horizontal: false, vertical: true)
                            .accessibilityIdentifier("fuel_error")
                            .announcedOnAppearance(message.localizedText)
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
                                .accessibilityLabel(Text("saving"))
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

    /// `E4-02`: the segmented control truncates its three labels at accessibility text sizes, so it
    /// becomes a menu there; the selection and the identifiers are the same in both styles.
    @ViewBuilder
    private var moneyModePicker: some View {
        let picker = Picker("money_input_method", selection: Binding(
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
        if dynamicTypeSize.isAccessibilitySize {
            picker.pickerStyle(.menu)
        } else {
            picker.pickerStyle(.segmented)
        }
    }
}

/// A value the shared holder derived from the two the owner typed (`E4-02`). At accessibility text
/// sizes the label and the value stack vertically, so neither is clipped.
private struct DerivedValueRow: View {
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    let labelKey: LocalizedStringKey
    let value: String?

    var body: some View {
        let layout = dynamicTypeSize.isAccessibilitySize
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: 4))
            : AnyLayout(HStackLayout())
        layout {
            Text(labelKey)
                .foregroundStyle(.primary)
            if !dynamicTypeSize.isAccessibilitySize {
                Spacer()
            }
            if let value {
                Text(value)
                    .fontWeight(.medium)
                    .accessibilityIdentifier("fuel_derived_value")
            } else {
                Text("value_pending")
                    .foregroundStyle(.primary)
                    .accessibilityIdentifier("fuel_derived_value")
            }
        }
    }
}
