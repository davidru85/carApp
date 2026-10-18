import Shared
import SwiftUI

/// The Settings screen of `docs/SPECIFICATION.md §3.1`, exactly: the currency, the read-only distance
/// and volume units, the backup status, the analytics opt-in, the account rows of `B1-D5` and the app
/// version. The anonymous-only "link account" row belongs to `E4-05`.
///
/// It is pushed onto the vehicle list's navigation stack rather than presented as the sheet design 06
/// draws: the departure prompts are presented from the root of the signed-in surface
/// (`DeparturePromptPresenter`), and a sheet would put a second modal between them and the owner. Every
/// change applies immediately, so there is no save action.
///
/// The screen holds no rule. Settings state comes from `SettingsStateHolder`; the backup row is the
/// `E3-05` indicator over `SyncStateHolder` with its `retryFailed()` action; the account rows call
/// `SessionStateHolder` intents through the process model, and their prompts and outcomes are drawn at
/// the root.
struct SettingsView: View {
    @StateObject private var viewModel: SettingsViewModel
    @ObservedObject private var skeletonModel: WalkingSkeletonModel
    private let appVersion: String

    init(
        graph: SwiftAppGraph,
        skeletonModel: WalkingSkeletonModel,
        appVersion: String = AppVersion.displayString()
    ) {
        _viewModel = StateObject(wrappedValue: SettingsViewModel(graph: graph))
        _skeletonModel = ObservedObject(wrappedValue: skeletonModel)
        self.appVersion = appVersion
    }

    var body: some View {
        List {
            Section(header: Text("settings_section_units").foregroundStyle(.primary)) {
                currencyRow
                SettingsUnitRows(state: viewModel.state)
                if let message = viewModel.state.message {
                    let text = message.localizedText
                    Text(text)
                        .font(.footnote)
                        .foregroundStyle(AccessibleColors.errorText)
                        .announcedOnAppearance(text)
                        .accessibilityIdentifier("settings_message")
                }
            }

            Section(header: Text("settings_section_backup").foregroundStyle(.primary)) {
                // The `E3-05` indicator: the published `§9.9` aggregate, the `D-193` labels, and the
                // retry that only the `Failed` visual offers.
                BackupStatusRow(model: skeletonModel)
                    .listRowInsets(EdgeInsets())
            }

            Section(header: Text("settings_section_privacy").foregroundStyle(.primary)) {
                // Off by default and applied at once (`docs/CONTRACTS.md §16.1`); the copy says
                // pseudonymous, as `D-203` requires. `R3`: `currencyCode == null` means the stored
                // settings are unknown, so the row shows the loading value and no switch at all — an off
                // switch would claim a stored opt-out the app has not read (`docs/CONTRACTS.md §20.10`).
                SettingsAnalyticsRow(
                    state: viewModel.state,
                    onChange: viewModel.setAnalyticsEnabled
                )
            }

            Section(header: Text("settings_section_account").foregroundStyle(.primary)) {
                LinkAccountRows(
                    state: skeletonModel.sessionState,
                    signInCoordinator: skeletonModel.signInCoordinator
                )
                ForEach(settingsSessionRows(for: skeletonModel.sessionState.phase), id: \.self) { row in
                    sessionRow(row)
                }
                if skeletonModel.sessionState.isBusy {
                    ProgressView()
                        .accessibilityLabel(Text("loading"))
                        .accessibilityIdentifier("settings_session_busy")
                }
            }

            Section(header: Text("settings_section_about").foregroundStyle(.primary)) {
                HStack {
                    Text("settings_version")
                    Spacer()
                    Text(appVersion)
                        .foregroundStyle(.primary)
                }
                .accessibilityElement(children: .combine)
                .accessibilityIdentifier("settings_version")
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle(Text("settings_title"))
        .onDisappear { viewModel.clearMessage() }
    }

    /// The currency is the one editable §3.1 value. It appears once the holder has read the stored
    /// settings, so the picker never shows a selection that is not stored.
    @ViewBuilder
    private var currencyRow: some View {
        if let currencyCode = viewModel.state.currencyCode {
            Picker(
                String(localized: "settings_currency"),
                selection: Binding(
                    get: { viewModel.state.currencyCode ?? currencyCode },
                    set: { viewModel.selectCurrency($0) }
                )
            ) {
                ForEach(viewModel.state.supportedCurrencyCodes, id: \.self) { code in
                    Text(currencyOptionLabel(code: code, locale: .current)).tag(code)
                }
            }
            .pickerStyle(.menu)
            .disabled(viewModel.state.isSaving)
            .accessibilityIdentifier("settings_currency_picker")
        } else {
            HStack {
                Text("settings_currency")
                Spacer()
                ProgressView()
                    .accessibilityLabel(Text("loading"))
            }
        }
    }

    @ViewBuilder
    private func sessionRow(_ row: SettingsSessionRow) -> some View {
        switch row {
        case .signOut:
            Button(action: skeletonModel.requestSignOut) {
                Text("settings_sign_out")
            }
            .disabled(skeletonModel.sessionState.isBusy)
            .accessibilityIdentifier("settings_sign_out")
        case .deleteAccount:
            Button(role: .destructive, action: skeletonModel.requestDeleteAccount) {
                Text("settings_delete_account")
            }
            .disabled(skeletonModel.sessionState.isBusy)
            .accessibilityIdentifier("settings_delete_account")
        case .deleteLocalData:
            Button(role: .destructive, action: skeletonModel.requestDeleteAccount) {
                Text("settings_delete_local_data")
                    .frame(minWidth: 44, minHeight: 44)
                    .contentShape(Rectangle())
            }
            .tint(AccessibleColors.controlAccent)
            .disabled(skeletonModel.sessionState.isBusy)
            .accessibilityIdentifier("settings_delete_local_data")
        }
    }
}

/// The two read-only unit rows of `§3.1`. While the stored settings are unknown
/// (`state.currencyCode == nil`) both values show the loading copy instead of a placeholder unit that
/// the app has not read (`docs/CONTRACTS.md §20.10`). Each row is one static accessibility element with
/// no action and no chevron. They are production components, so the Debug review fixtures render the
/// same view rather than a second implementation.
struct SettingsUnitRows: View {
    let state: SettingsUiState

    var body: some View {
        readOnlyRow(
            labelKey: "settings_distance_unit",
            valueKey: state.currencyCode == nil ? "loading" : distanceUnitLabelKey(state.distanceUnit),
            identifier: "settings_distance_unit"
        )
        readOnlyRow(
            labelKey: "settings_volume_unit",
            valueKey: state.currencyCode == nil ? "loading" : volumeUnitLabelKey(state.volumeUnit),
            identifier: "settings_volume_unit"
        )
    }

    private func readOnlyRow(labelKey: String, valueKey: String, identifier: String) -> some View {
        HStack {
            Text(LocalizedStringKey(labelKey))
            Spacer()
            Text(LocalizedStringKey(valueKey))
                .foregroundStyle(.primary)
        }
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier(identifier)
    }
}

/// The `§16.1` row as `SettingsView` declares it, so the Debug review fixtures render the same production
/// shape: the loading row while the stored settings are unknown, and the switch after.
struct SettingsAnalyticsRow: View {
    let state: SettingsUiState
    let onChange: (Bool) -> Void

    var body: some View {
        if state.currencyCode == nil {
            HStack {
                Text("settings_analytics")
                Spacer()
                Text("loading")
            }
            .accessibilityIdentifier("settings_analytics_loading")
        } else {
            Toggle(isOn: Binding(
                get: { state.analyticsEnabled },
                set: { onChange($0) }
            )) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("settings_analytics")
                    Text("settings_analytics_description")
                        .font(.footnote)
                        .foregroundStyle(.primary)
                }
            }
            .disabled(state.isSaving)
            .accessibilityIdentifier("settings_analytics_toggle")
        }
    }
}
