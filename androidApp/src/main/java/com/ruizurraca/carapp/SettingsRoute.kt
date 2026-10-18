package com.ruizurraca.carapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.ruizurraca.carapp.core.common.AuthProvider

/**
 * The `E4-01` Settings destination, pushed over the vehicle list. Its holders are ViewModel-scoped like
 * the session and sync holders, so leaving the route releases nothing; the destination only binds the
 * stateless [SettingsScreen] to existing shared intents.
 */
internal fun NavGraphBuilder.settingsRoute(
    navController: NavHostController,
    viewModel: VehicleAppViewModel,
    googleSignIn: GoogleSignInActions,
) {
    composable(VehicleRoutes.SETTINGS) {
        SettingsRoute(
            viewModel = viewModel,
            googleSignIn = googleSignIn,
            onBack = { navController.popBackStack() },
        )
    }
}

/**
 * B1-D5 binds both deletion rows to `requestDeleteAccount()`: the shared holder decides which of the
 * three `DELETING` operations the current owner is entitled to (`docs/CONTRACTS.md §20.10`, `D-158`).
 */
@Composable
private fun SettingsRoute(
    viewModel: VehicleAppViewModel,
    googleSignIn: GoogleSignInActions,
    onBack: () -> Unit,
) {
    val settings by viewModel.settingsStateHolder.state.collectAsState()
    val session by viewModel.sessionStateHolder.state.collectAsState()
    val sync by viewModel.syncStateHolder.state.collectAsState()
    val context = LocalContext.current
    val appVersion = remember(context) { context.appVersionName() }
    SettingsScreen(
        settings = settings,
        session = session,
        sync = sync,
        appVersion = appVersion,
        callbacks =
            SettingsCallbacks(
                onBack = onBack,
                onSelectCurrency = viewModel.settingsStateHolder::selectCurrency,
                onAnalyticsEnabledChange = viewModel.settingsStateHolder::setAnalyticsEnabled,
                onRetryBackup = viewModel.syncStateHolder::retryFailed,
                onSignOut = viewModel.sessionStateHolder::requestSignOut,
                onDeleteAccount = viewModel.sessionStateHolder::requestDeleteAccount,
                onDeleteLocalData = viewModel.sessionStateHolder::requestDeleteAccount,
                onDismissSettingsMessage = viewModel.settingsStateHolder::clearMessage,
                onLinkAccount = { provider ->
                    if (provider == AuthProvider.GOOGLE) googleSignIn.convert()
                },
            ),
    )
}
