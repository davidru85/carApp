import SwiftUI
import Shared

struct VehicleDetailView: View {
    @Environment(\.dismiss) private var dismiss
    let graph: SwiftAppGraph
    let vehicleId: String
    let vehicleName: String

    @StateObject private var viewModel: FuelEntryListViewModel
    @State private var editingVehicle = false
    @State private var activeFuelEntryId: String? = nil
    @State private var isCreatingFuelEntry = false
    @State private var isDeleteAlertPresented = false

    private let calendarDay = FuelEntryCalendarDay()

    init(graph: SwiftAppGraph, vehicleId: String, vehicleName: String) {
        self.graph = graph
        self.vehicleId = vehicleId
        self.vehicleName = vehicleName
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
                        .foregroundColor(.secondary)
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
            }
            .accessibilityIdentifier("add_fuel_entry")
        }
    }
}

struct HeroConsumptionCardView: View {
    let averageScaled: KotlinLong?
    let validSegmentCount: Int32
    let isReliable: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("consumption_average_title")
                .font(.subheadline)
                .foregroundColor(.secondary)

            if let avg = averageScaled {
                let avgFormatted = formatScaled(avg.int64Value, scale: ScaledFormat.consumptionScale)
                Text("\(avgFormatted) L/100 km")
                    .font(.system(size: 32, weight: .bold, design: .rounded))
                    .accessibilityIdentifier("consumption_average")

                if isReliable {
                    let text = String(format: String(localized: "consumption_reliable"), validSegmentCount)
                    Text(text)
                        .font(.caption)
                        .foregroundColor(.secondary)
                } else {
                    let text = String(format: String(localized: "consumption_not_yet_reliable"), validSegmentCount)
                    Text(text)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            } else {
                Text("consumption_empty")
                    .font(.body)
                    .foregroundColor(.secondary)
                    .accessibilityIdentifier("consumption_empty")
            }
        }
        .padding(.vertical, 4)
    }
}

struct FuelEntryRowView: View {
    let entry: FuelEntryListItemUi
    let calendarDay: FuelEntryCalendarDay

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
        HStack {
            Text(calendarDay.format(epochMillis: entry.dateEpochMillis))
                .font(.headline)
            Spacer()
            if let consumption = entry.consumptionScaled {
                Text("\(formatScaled(consumption.int64Value, scale: ScaledFormat.consumptionScale)) L/100 km")
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .foregroundColor(.accentColor)
                    .accessibilityIdentifier("fuel_explanation_\(entry.id)")
            } else {
                Text(consumptionExplanation(for: entry.invalidReason))
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .accessibilityIdentifier("fuel_explanation_\(entry.id)")
            }
        }
    }

    private var factsRow: some View {
        let litersText = formatScaled(entry.litersScaled, scale: ScaledFormat.litersScale)
        let totalCostText = formatScaled(entry.totalCostMinor, scale: ScaledFormat.minorUnitScale)
        let formatTemplate = String(localized: "fuel_entry_facts")
        let facts = String(format: formatTemplate, entry.odometerKm, litersText, totalCostText, entry.currencyCode)
        return Text(facts)
            .font(.caption)
            .foregroundColor(.secondary)
    }

    private var indicatorsRow: some View {
        HStack(spacing: 6) {
            if !entry.isFullTank {
                Text("partial_tank")
                    .font(.caption2)
                    .fontWeight(.medium)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Color.orange.opacity(0.15))
                    .foregroundColor(.orange)
                    .cornerRadius(4)
                    .accessibilityIdentifier("fuel_indicator_\(entry.id)_partial")
            }
            if entry.hasMissedEntries {
                Text("missed_entries")
                    .font(.caption2)
                    .fontWeight(.medium)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Color.red.opacity(0.15))
                    .foregroundColor(.red)
                    .cornerRadius(4)
                    .accessibilityIdentifier("fuel_indicator_\(entry.id)_missed")
            }
            if entry.odometerInconsistent {
                Text("inconsistent_odometer")
                    .font(.caption2)
                    .fontWeight(.medium)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Color.purple.opacity(0.15))
                    .foregroundColor(.purple)
                    .cornerRadius(4)
                    .accessibilityIdentifier("fuel_indicator_\(entry.id)_odometer")
            }
        }
    }
}

struct IdentifiableString: Identifiable {
    let id: String
}
