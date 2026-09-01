import Foundation
import Shared
import SwiftUI

@MainActor
final class VehicleListViewModel: ObservableObject {
    @Published private(set) var state: VehicleListUiState

    private let graph: SwiftAppGraph
    private let stateHolder: VehicleListStateHolder
    private var observationTask: Task<Void, Never>?

    init(graph: SwiftAppGraph) {
        self.graph = graph
        let holder = graph.vehicleListStateHolder()
        self.stateHolder = holder
        self.state = holder.state.value

        self.observationTask = Task { [weak self, holder] in
            for await s in holder.state {
                self?.state = s
            }
        }
    }

    func refresh() {
        stateHolder.refresh()
    }

    func selectVehicle(_ id: String?) {
        stateHolder.selectVehicle(vehicleId: id)
    }

    func requestDelete(_ id: String) {
        stateHolder.requestDelete(vehicleId: id)
    }

    func confirmDelete(_ id: String) {
        stateHolder.confirmDelete(vehicleId: id)
    }

    func clearMessage() {
        stateHolder.clearMessage()
    }

    deinit {
        observationTask?.cancel()
        stateHolder.close()
    }
}

@MainActor
final class VehicleFormViewModel: ObservableObject {
    @Published private(set) var state: VehicleFormUiState
    @Published var name: String = ""
    @Published var odometerText: String = ""
    @Published var brand: String = ""
    @Published var model: String = ""
    @Published var hasOdometerError: Bool = false
    @Published var isSaveComplete: Bool = false

    private let graph: SwiftAppGraph
    let vehicleId: String?
    private let stateHolder: VehicleFormStateHolder
    private var observationTask: Task<Void, Never>?
    private var wasSaving: Bool = false
    private var hasEditedName: Bool = false
    private var hasEditedOdometer: Bool = false
    private var hasEditedBrand: Bool = false
    private var hasEditedModel: Bool = false
    private var onSaveCallback: (() -> Void)? = nil

    init(graph: SwiftAppGraph, vehicleId: String?) {
        self.graph = graph
        self.vehicleId = vehicleId
        let holder = graph.vehicleFormStateHolder(vehicleId: vehicleId)
        self.stateHolder = holder
        let initial = holder.state.value
        self.state = initial
        self.name = initial.name
        self.odometerText = initial.initialOdometerKm == 0 ? "" : String(initial.initialOdometerKm)
        self.brand = initial.brand ?? ""
        self.model = initial.model ?? ""

        self.observationTask = Task { [weak self, holder] in
            for await s in holder.state {
                self?.handleStateUpdate(s)
            }
        }
    }

    private func handleStateUpdate(_ s: VehicleFormUiState) {
        if !hasEditedName {
            name = s.name
        }
        if !hasEditedOdometer && s.canEditInitialOdometer {
            odometerText = s.initialOdometerKm == 0 ? "" : String(s.initialOdometerKm)
        }
        if !hasEditedBrand {
            brand = s.brand ?? ""
        }
        if !hasEditedModel {
            model = s.model ?? ""
        }

        if vehicleId == nil {
            if s.savedVehicleId != nil && !s.isSaving {
                isSaveComplete = true
                wasSaving = false
                onSaveCallback?()
                onSaveCallback = nil
            }
        } else {
            if wasSaving && !s.isSaving {
                if s.message == nil {
                    isSaveComplete = true
                    wasSaving = false
                    onSaveCallback?()
                    onSaveCallback = nil
                } else {
                    wasSaving = false
                }
            }
        }
        state = s
    }

    func setName(_ val: String) {
        hasEditedName = true
        name = val
        stateHolder.setName(value: val)
    }

    func setOdometerText(_ text: String) {
        guard isValidOdometerText(text) else { return }
        hasEditedOdometer = true
        odometerText = text
        if let km = Int64(text), km >= 0, km <= 2_000_000 {
            hasOdometerError = false
            stateHolder.setInitialOdometerKm(value: km)
        } else {
            hasOdometerError = !text.isEmpty
        }
    }

    func setBrand(_ val: String) {
        hasEditedBrand = true
        brand = val
        stateHolder.setBrand(value: val.isEmpty ? nil : val)
    }

    func setModel(_ val: String) {
        hasEditedModel = true
        model = val
        stateHolder.setModel(value: val.isEmpty ? nil : val)
    }

    func save(onSuccess: (() -> Void)? = nil) {
        guard !state.isSaving, !hasOdometerError else { return }
        wasSaving = true
        self.onSaveCallback = onSuccess
        stateHolder.save()
    }

    func clearMessage() {
        stateHolder.clearMessage()
    }

    deinit {
        observationTask?.cancel()
        stateHolder.close()
        graph.releaseVehicleFormStateHolder(vehicleId: vehicleId)
    }
}

@MainActor
final class FuelEntryListViewModel: ObservableObject {
    @Published private(set) var state: FuelEntryListUiState

    private let graph: SwiftAppGraph
    let vehicleId: String
    private let stateHolder: FuelEntryListStateHolder
    private var observationTask: Task<Void, Never>?

    init(graph: SwiftAppGraph, vehicleId: String) {
        self.graph = graph
        self.vehicleId = vehicleId
        let holder = graph.fuelEntryListStateHolder(vehicleId: vehicleId)
        self.stateHolder = holder
        self.state = holder.state.value

        self.observationTask = Task { [weak self, holder] in
            for await s in holder.state {
                self?.state = s
            }
        }
    }

    func refresh() {
        stateHolder.refresh()
    }

    func requestDelete(_ id: String) {
        stateHolder.requestDelete(entryId: id)
    }

    func confirmDelete(_ id: String) {
        stateHolder.confirmDelete(entryId: id)
    }

    func clearMessage() {
        stateHolder.clearMessage()
    }

    deinit {
        observationTask?.cancel()
        stateHolder.close()
        graph.releaseFuelEntryListStateHolder(vehicleId: vehicleId)
    }
}

@MainActor
final class FuelEntryFormViewModel: ObservableObject {
    @Published private(set) var state: FuelEntryFormUiState
    @Published var odometerText: String = ""
    @Published var litersText: String = ""
    @Published var priceText: String = ""
    @Published var totalText: String = ""
    @Published var notesText: String = ""
    @Published var selectedDate: Date = Date()
    @Published var isSaveComplete: Bool = false

    private let graph: SwiftAppGraph
    let vehicleId: String
    let entryId: String?
    private let stateHolder: FuelEntryFormStateHolder
    let calendarDay: FuelEntryCalendarDay
    private var observationTask: Task<Void, Never>?
    private var hasEditedOdometer = false
    private var hasEditedLiters = false
    private var hasEditedPrice = false
    private var hasEditedTotal = false
    private var hasEditedNotes = false
    private var onSaveCallback: (() -> Void)? = nil
    private var wasSaving: Bool = false

    init(
        graph: SwiftAppGraph,
        vehicleId: String,
        entryId: String?,
        calendarDay: FuelEntryCalendarDay = FuelEntryCalendarDay()
    ) {
        self.graph = graph
        self.vehicleId = vehicleId
        self.entryId = entryId
        let holder = graph.fuelEntryFormStateHolder(vehicleId: vehicleId, entryId: entryId)
        self.stateHolder = holder
        self.calendarDay = calendarDay
        let initial = holder.state.value
        self.state = initial
        self.selectedDate = calendarDay.date(from: initial.dateEpochMillis)
        self.odometerText = initial.odometerKm == 0 ? "" : String(initial.odometerKm)
        self.litersText = initial.litersScaled != nil ? formatScaled(initial.litersScaled!.int64Value, scale: ScaledFormat.litersScale) : ""
        self.priceText = initial.pricePerLiterScaled != nil ? formatScaled(initial.pricePerLiterScaled!.int64Value, scale: ScaledFormat.pricePerLiterScale) : ""
        self.totalText = initial.totalCostMinor != nil ? formatScaled(initial.totalCostMinor!.int64Value, scale: ScaledFormat.minorUnitScale) : ""
        self.notesText = initial.notes ?? ""

        self.observationTask = Task { [weak self, holder] in
            for await s in holder.state {
                self?.handleStateUpdate(s)
            }
        }
    }

    private func handleStateUpdate(_ s: FuelEntryFormUiState) {
        if !hasEditedOdometer {
            odometerText = s.odometerKm == 0 ? "" : String(s.odometerKm)
        }
        if !hasEditedNotes {
            notesText = s.notes ?? ""
        }

        switch s.moneyInputMode {
        case .litersAndPrice:
            if !hasEditedLiters {
                litersText = s.litersScaled != nil ? formatScaled(s.litersScaled!.int64Value, scale: ScaledFormat.litersScale) : ""
            }
            if !hasEditedPrice {
                priceText = s.pricePerLiterScaled != nil ? formatScaled(s.pricePerLiterScaled!.int64Value, scale: ScaledFormat.pricePerLiterScale) : ""
            }
            totalText = s.totalCostMinor != nil ? formatScaled(s.totalCostMinor!.int64Value, scale: ScaledFormat.minorUnitScale) : ""
        case .litersAndTotal:
            if !hasEditedLiters {
                litersText = s.litersScaled != nil ? formatScaled(s.litersScaled!.int64Value, scale: ScaledFormat.litersScale) : ""
            }
            if !hasEditedTotal {
                totalText = s.totalCostMinor != nil ? formatScaled(s.totalCostMinor!.int64Value, scale: ScaledFormat.minorUnitScale) : ""
            }
            priceText = s.pricePerLiterScaled != nil ? formatScaled(s.pricePerLiterScaled!.int64Value, scale: ScaledFormat.pricePerLiterScale) : ""
        case .priceAndTotal:
            if !hasEditedPrice {
                priceText = s.pricePerLiterScaled != nil ? formatScaled(s.pricePerLiterScaled!.int64Value, scale: ScaledFormat.pricePerLiterScale) : ""
            }
            if !hasEditedTotal {
                totalText = s.totalCostMinor != nil ? formatScaled(s.totalCostMinor!.int64Value, scale: ScaledFormat.minorUnitScale) : ""
            }
            litersText = s.litersScaled != nil ? formatScaled(s.litersScaled!.int64Value, scale: ScaledFormat.litersScale) : ""
        }

        if wasSaving && !s.isSaving {
            if s.message == nil {
                isSaveComplete = true
                wasSaving = false
                onSaveCallback?()
                onSaveCallback = nil
            } else {
                wasSaving = false
            }
        }

        state = s
    }

    func setDate(_ date: Date) {
        selectedDate = date
        let millis = calendarDay.startOfDay(for: date)
        stateHolder.setDateEpochMillis(value: millis)
    }

    func setOdometerText(_ text: String) {
        guard isValidOdometerText(text) else { return }
        hasEditedOdometer = true
        odometerText = text
        stateHolder.setOdometerKm(value: Int64(text) ?? 0)
    }

    func setMoneyInputMode(_ mode: MoneyInputMode) {
        hasEditedLiters = false
        hasEditedPrice = false
        hasEditedTotal = false
        stateHolder.setMoneyInputMode(value: mode)
    }

    func setLitersText(_ text: String) {
        let accepted = acceptScaledInput(previous: litersText, candidate: text, scale: ScaledFormat.litersScale)
        hasEditedLiters = true
        litersText = accepted
        let parsed = parseScaled(accepted, scale: ScaledFormat.litersScale)
        stateHolder.setLitersScaled(value: parsed != nil ? KotlinLong(value: parsed!) : nil)
    }

    func setPricePerLiterText(_ text: String) {
        let accepted = acceptScaledInput(previous: priceText, candidate: text, scale: ScaledFormat.pricePerLiterScale)
        hasEditedPrice = true
        priceText = accepted
        let parsed = parseScaled(accepted, scale: ScaledFormat.pricePerLiterScale)
        stateHolder.setPricePerLiterScaled(value: parsed != nil ? KotlinLong(value: parsed!) : nil)
    }

    func setTotalCostText(_ text: String) {
        let accepted = acceptScaledInput(previous: totalText, candidate: text, scale: ScaledFormat.minorUnitScale)
        hasEditedTotal = true
        totalText = accepted
        let parsed = parseScaled(accepted, scale: ScaledFormat.minorUnitScale)
        stateHolder.setTotalCostMinor(value: parsed != nil ? KotlinLong(value: parsed!) : nil)
    }

    func setFullTank(_ value: Bool) {
        stateHolder.setFullTank(value: value)
    }

    func setMissedEntries(_ value: Bool) {
        stateHolder.setMissedEntries(value: value)
    }

    func setNotes(_ value: String) {
        hasEditedNotes = true
        notesText = value
        stateHolder.setNotes(value: value.isEmpty ? nil : value)
    }

    func save(onSuccess: (() -> Void)? = nil) {
        guard !state.isSaving else { return }
        wasSaving = true
        self.onSaveCallback = onSuccess
        stateHolder.save()
    }

    func confirmOdometerWarning(onSuccess: (() -> Void)? = nil) {
        wasSaving = true
        self.onSaveCallback = onSuccess
        stateHolder.confirmSave(confirmation: Confirmation.odometerInconsistent)
    }

    func clearMessage() {
        stateHolder.clearMessage()
    }

    deinit {
        observationTask?.cancel()
        stateHolder.close()
        graph.releaseFuelEntryFormStateHolder(vehicleId: vehicleId, entryId: entryId)
    }
}

@MainActor
final class DiagnosticsViewModel: ObservableObject {
    @Published private(set) var sessionState: SessionUiState
    private let graph: SwiftAppGraph
    private let sessionStateHolder: SessionStateHolder
    private let vehicleListStateHolder: VehicleListStateHolder
    private var observationTask: Task<Void, Never>?

    init(graph: SwiftAppGraph) {
        self.graph = graph
        self.sessionStateHolder = graph.sessionStateHolder()
        self.vehicleListStateHolder = graph.vehicleListStateHolder()
        self.sessionState = sessionStateHolder.state.value

        self.observationTask = Task { [weak self, sessionStateHolder] in
            for await s in sessionStateHolder.state {
                self?.sessionState = s
            }
        }
    }

    var canStartAnonymousSession: Bool {
        sessionState.phase == .signedOut || sessionState.phase == .local
    }

    var sessionLabel: String {
        switch sessionState.phase {
        case .unknown:
            return String(localized: "session_unknown")
        case .local:
            return String(localized: "session_local")
        case .anonymous:
            return String(localized: "session_anonymous")
        case .permanent:
            return String(localized: "session_permanent")
        case .signedOut:
            return String(localized: "session_signed_out")
        case .deleting:
            return String(localized: "session_deleting")
        }
    }

    func startAnonymousSession() {
        sessionStateHolder.startAnonymousSignIn()
    }

    func restoreBackup() {
        vehicleListStateHolder.refresh()
    }

    deinit {
        observationTask?.cancel()
        sessionStateHolder.close()
    }
}
