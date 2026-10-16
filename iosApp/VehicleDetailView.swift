import SwiftUI
import Shared

struct VehicleDetailView: View {
    @Environment(\.dismiss) private var dismiss
    let graph: SwiftAppGraph
    let vehicleId: String
    @StateObject private var vehicleListViewModel: VehicleListViewModel

    @StateObject private var viewModel: FuelEntryListViewModel
    @State private var editingVehicle = false
    @State private var activeFuelEntryId: String? = nil
    @State private var isCreatingFuelEntry = false
    @State private var isDeleteAlertPresented = false

    private let calendarDay = FuelEntryCalendarDay()

    /// The title comes from persisted state, which carries the canonical name the domain produced
    /// when the vehicle was validated. Nothing here reproduces that normalization.
    private var vehicleName: String {
        vehicleListViewModel.state.vehicles.first { $0.id == vehicleId }?.name ?? ""
    }

    init(graph: SwiftAppGraph, vehicleId: String) {
        self.graph = graph
        self.vehicleId = vehicleId
        _vehicleListViewModel = StateObject(wrappedValue: VehicleListViewModel(graph: graph))
        _viewModel = StateObject(wrappedValue: FuelEntryListViewModel(graph: graph, vehicleId: vehicleId))
    }

    var body: some View {
        List {
            Section {
                HeroConsumptionCardView(
                    averageScaled: viewModel.state.consumptionAverageScaled,
                    validSegmentCount: viewModel.state.validConsumptionSegmentCount,
                    isReliable: viewModel.state.isConsumptionReliable
                )
            }

            Section(header: fuelEntriesHeader) {
                if viewModel.state.entries.isEmpty {
                    Text("add_first_fuel_entry_invitation")
                        .font(.subheadline)
                        .foregroundStyle(.primary)
                        .padding(.vertical, 8)
                        .accessibilityIdentifier("first_fuel_invitation")
                } else {
                    ForEach(viewModel.state.entries, id: \.id) { entry in
                        FuelEntryRowView(entry: entry, calendarDay: calendarDay)
                            .contentShape(Rectangle())
                            .onTapGesture {
                                activeFuelEntryId = entry.id
                            }
                            .accessibilityIdentifier("fuel_row_\(entry.id)")
                            .swipeActions(edge: .trailing) {
                                Button(role: .destructive) {
                                    viewModel.confirmDelete(entry.id)
                                } label: {
                                    Label("delete", systemImage: "trash")
                                }
                            }
                    }
                }
            }
        }
        .navigationTitle(vehicleName)
        .accessibilityIdentifier("vehicle_detail_name")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Menu {
                    Button {
                        editingVehicle = true
                    } label: {
                        Label("edit_vehicle", systemImage: "pencil")
                    }
                    .accessibilityIdentifier("edit_vehicle")

                    Button(role: .destructive) {
                        isDeleteAlertPresented = true
                    } label: {
                        Label("delete_vehicle", systemImage: "trash")
                    }
                    .accessibilityIdentifier("delete_vehicle")
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
                .accessibilityLabel(Text("vehicle_more_actions"))
                .accessibilityIdentifier("vehicle_more_actions")
            }
        }
        .alert(String(localized: "delete_vehicle_title"), isPresented: $isDeleteAlertPresented) {
            Button(String(localized: "delete"), role: .destructive) {
                let listHolder = graph.vehicleListStateHolder()
                listHolder.confirmDelete(vehicleId: vehicleId)
                dismiss()
            }
            Button(String(localized: "cancel"), role: .cancel) {}
        } message: {
            Text("delete_vehicle_confirmation")
        }
        .sheet(isPresented: $editingVehicle) {
            VehicleFormView(graph: graph, vehicleId: vehicleId) {
                editingVehicle = false
            }
        }
        .sheet(isPresented: $isCreatingFuelEntry) {
            FuelEntryFormView(graph: graph, vehicleId: vehicleId, entryId: nil) {
                isCreatingFuelEntry = false
            }
        }
        .sheet(item: Binding(
            get: { activeFuelEntryId.map { IdentifiableString(id: $0) } },
            set: { activeFuelEntryId = $0?.id }
        )) { item in
            FuelEntryFormView(graph: graph, vehicleId: vehicleId, entryId: item.id) {
                activeFuelEntryId = nil
            }
        }
    }

    private var fuelEntriesHeader: some View {
        HStack {
            Text("add_fuel_entry")
            Spacer()
            Button(action: {
                isCreatingFuelEntry = true
            }) {
                Image(systemName: "plus.circle.fill")
                    .font(.title3)
                    // `E4-02`: the 44-point minimum hit region of the Human Interface Guidelines.
                    .frame(minWidth: 44, minHeight: 44)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel(Text("add_fuel_entry"))
            .accessibilityIdentifier("add_fuel_entry")
        }
    }
}

/// The consumption value with its unit from the catalogue, formatted for the current locale
/// (`E4-02`, `D-222`): `7,24 L/100 km` in Spanish.
func consumptionText(_ scaled: Int64) -> String {
    String(
        format: String(localized: "consumption_value"),
        formatScaledDecimal(scaled, scale: ScaledFormat.consumptionScale, locale: .current)
    )
}

struct HeroConsumptionCardView: View {
    let averageScaled: KotlinLong?
    let validSegmentCount: Int32
    let isReliable: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("consumption_average_title")
                .font(.subheadline)
                .foregroundStyle(.primary)
                .accessibilityAddTraits(.isHeader)

            if let avg = averageScaled {
                // `E4-02`: a Dynamic Type style instead of a fixed 32-point size.
                Text(consumptionText(avg.int64Value))
                    .font(.system(.largeTitle, design: .rounded, weight: .bold))
                    .accessibilityIdentifier("consumption_average")

                if isReliable {
                    let text = String(format: String(localized: "consumption_reliable"), validSegmentCount)
                    Text(text)
                        .font(.caption)
                        .foregroundStyle(.primary)
                } else {
                    let text = String(format: String(localized: "consumption_not_yet_reliable"), validSegmentCount)
                    Text(text)
                        .font(.caption)
                        .foregroundStyle(.primary)
                }
            } else {
                Text("consumption_empty")
                    .font(.body)
                    .foregroundStyle(.primary)
                    .accessibilityIdentifier("consumption_empty")
            }
        }
        .padding(.vertical, 4)
    }
}

struct FuelEntryRowView: View {
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    let entry: FuelEntryListItemUi
    let calendarDay: FuelEntryCalendarDay

    /// `E4-02`: at accessibility text sizes the header and the badges stack vertically, so they wrap
    /// instead of clipping.
    private var adaptiveLayout: AnyLayout {
        dynamicTypeSize.isAccessibilitySize
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: 4))
            : AnyLayout(HStackLayout(spacing: 6))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            headerRow
            factsRow
            indicatorsRow
        }
        .padding(.vertical, 2)
        .accessibilityElement(children: .contain)
    }

    private var headerRow: some View {
        adaptiveLayout {
            Text(calendarDay.format(epochMillis: entry.dateEpochMillis))
                .font(.headline)
            if !dynamicTypeSize.isAccessibilitySize {
                Spacer()
            }
            if let consumption = entry.consumptionScaled {
                // `E4-02`, `D-223`: the accent colour measured 4.02:1 on white (C6), so the value
                // uses the primary label colour.
                Text(consumptionText(consumption.int64Value))
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .foregroundStyle(.primary)
                    .accessibilityIdentifier("fuel_explanation_\(entry.id)")
            } else {
                Text(consumptionExplanation(for: entry.invalidReason))
                    .font(.caption)
                    .foregroundStyle(.primary)
                    .accessibilityIdentifier("fuel_explanation_\(entry.id)")
            }
        }
    }

    private var factsRow: some View {
        let locale = Locale.current
        let facts = String(
            format: String(localized: "fuel_entry_facts"),
            formatInteger(entry.odometerKm, locale: locale),
            formatScaledDecimal(entry.litersScaled, scale: ScaledFormat.litersScale, locale: locale),
            formatCurrencyAmount(
                entry.totalCostMinor,
                scale: ScaledFormat.minorUnitScale,
                currencyCode: entry.currencyCode,
                locale: locale
            )
        )
        return Text(facts)
            .font(.caption)
            .foregroundStyle(.primary)
    }

    private var indicatorsRow: some View {
        adaptiveLayout {
            if !entry.isFullTank {
                IndicatorBadge(key: "partial_tank", tint: .orange)
                    .accessibilityIdentifier("fuel_indicator_\(entry.id)_partial")
            }
            if entry.hasMissedEntries {
                IndicatorBadge(key: "missed_entries", tint: .red)
                    .accessibilityIdentifier("fuel_indicator_\(entry.id)_missed")
            }
            if entry.odometerInconsistent {
                IndicatorBadge(key: "inconsistent_odometer", tint: .purple)
                    .accessibilityIdentifier("fuel_indicator_\(entry.id)_odometer")
            }
        }
    }
}

/// A tinted fact badge (`E4-02`, `D-223`). Its text uses the primary label colour, because the
/// tint colours measured below the 4.5:1 text contrast on their own 15 % background: orange 1.95:1,
/// red 2.91:1 and purple 3.42:1 in light appearance (C1–C3). The tint stays as background and border.
private struct IndicatorBadge: View {
    let key: LocalizedStringKey
    let tint: Color

    var body: some View {
        Text(key)
            .font(.caption2)
            .fontWeight(.medium)
            .foregroundStyle(.primary)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(tint.opacity(0.15), in: RoundedRectangle(cornerRadius: 4))
            .overlay(RoundedRectangle(cornerRadius: 4).strokeBorder(tint, lineWidth: 1))
    }
}

struct IdentifiableString: Identifiable {
    let id: String
}
