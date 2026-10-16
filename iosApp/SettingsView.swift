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

    private var analyticsEnabled: Binding<Bool> {
        Binding(
            get: { viewModel.state.analyticsEnabled },
            set: { viewModel.setAnalyticsEnabled($0) }
        )
    }

    var body: some View {
        List {
            Section(header: Text("settings_section_units")) {
                currencyRow
                readOnlyRow(
                    labelKey: "settings_distance_unit",
                    valueKey: distanceUnitLabelKey(viewModel.state.distanceUnit),
                    identifier: "settings_distance_unit"
                )
                readOnlyRow(
                    labelKey: "settings_volume_unit",
                    valueKey: volumeUnitLabelKey(viewModel.state.volumeUnit),
                    identifier: "settings_volume_unit"
                )
                if let message = viewModel.state.message {
                    Text(message.localizedText)
                        .font(.footnote)
                        .foregroundColor(.red)
                        .accessibilityIdentifier("settings_message")
                }
            }

            Section(header: Text("settings_section_backup")) {
                // The `E3-05` indicator: the published `§9.9` aggregate, the `D-193` labels, and the
                // retry that only the `Failed` visual offers.
                BackupStatusRow(model: skeletonModel)
                    .listRowInsets(EdgeInsets())
            }

            Section(header: Text("settings_section_privacy")) {
                // Off by default and applied at once (`docs/CONTRACTS.md §16.1`); the copy says
                // pseudonymous, as `D-203` requires.
                Toggle(isOn: analyticsEnabled) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("settings_analytics")
                        Text("settings_analytics_description")
                            .font(.footnote)
                            .foregroundColor(.secondary)
                    }
                }
                .disabled(viewModel.state.isSaving)
                .accessibilityIdentifier("settings_analytics_toggle")
            }

            Section(header: Text("settings_section_account")) {
                ForEach(settingsSessionRows(for: skeletonModel.sessionState.phase), id: \.self) { row in
                    sessionRow(row)
                }
                if skeletonModel.sessionState.isBusy {
                    ProgressView()
                        .accessibilityIdentifier("settings_session_busy")
                }
            }

            Section(header: Text("settings_section_about")) {
                HStack {
                    Text("settings_version")
                    Spacer()
                    Text(appVersion)
                        .foregroundColor(.secondary)
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
            }
        }
    }

    /// A read-only value (`§3.1`): one static accessibility element with no action and no chevron.
    private func readOnlyRow(labelKey: String, valueKey: String, identifier: String) -> some View {
        HStack {
            Text(LocalizedStringKey(labelKey))
            Spacer()
            Text(LocalizedStringKey(valueKey))
                .foregroundColor(.secondary)
        }
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier(identifier)
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
            }
            .disabled(skeletonModel.sessionState.isBusy)
            .accessibilityIdentifier("settings_delete_local_data")
        }
    }
}
