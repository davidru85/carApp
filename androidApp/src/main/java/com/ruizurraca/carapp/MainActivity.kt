package com.ruizurraca.carapp

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ruizurraca.carapp.wiring.firebase.firebaseAppProviders

class MainActivity : ComponentActivity() {
    private lateinit var graph: SwiftAppGraph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val providers =
            firebaseAppProviders(
                databaseFilePath = getDatabasePath(DATABASE_FILE_NAME).absolutePath,
            )
        val isDebugBuild = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        graph = buildAppGraph(isDebugBuild = isDebugBuild, providers = providers)

        setContent {
            MaterialTheme {
                Surface {
                    WalkingSkeletonScreen(graph = graph)
                }
            }
        }
    }

    override fun onDestroy() {
        if (::graph.isInitialized) graph.close()
        super.onDestroy()
    }
}

@Composable
private fun WalkingSkeletonScreen(graph: SwiftAppGraph) {
    val sessionStateHolder = remember(graph) { graph.sessionStateHolder() }
    val vehicleFormStateHolder = remember(graph) { graph.vehicleFormStateHolder(vehicleId = null) }
    val vehicleListStateHolder = remember(graph) { graph.vehicleListStateHolder() }
    val sessionState by sessionStateHolder.state.collectAsState()
    val vehicleFormState by vehicleFormStateHolder.state.collectAsState()
    val vehicleListState by vehicleListStateHolder.state.collectAsState()

    DisposableEffect(vehicleFormStateHolder) {
        onDispose { vehicleFormStateHolder.close() }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.walking_skeleton_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        SessionSection(
            state = sessionState,
            onStartAnonymousSignIn = { sessionStateHolder.startAnonymousSignIn() },
        )
        VehicleEditor(
            state = vehicleFormState,
            onNameChange = vehicleFormStateHolder::setName,
            onSave = { vehicleFormStateHolder.save() },
            onRefresh = { vehicleListStateHolder.refresh() },
            isRefreshing = vehicleListState.isLoading,
        )
        VehicleList(state = vehicleListState)
    }
}

@Composable
private fun SessionSection(
    state: SessionUiState,
    onStartAnonymousSignIn: () -> Unit,
) {
    Text(text = stringResource(R.string.session_status, state.phase.localizedLabel()))
    if (state.phase == SessionPhase.SIGNED_OUT || state.phase == SessionPhase.LOCAL) {
        Button(onClick = onStartAnonymousSignIn, enabled = !state.isBusy) {
            Text(stringResource(R.string.continue_without_account))
        }
    }
    if (state.isBusy) CircularProgressIndicator()
}

@Composable
private fun VehicleEditor(
    state: VehicleFormUiState,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
) {
    OutlinedTextField(
        value = state.name,
        onValueChange = onNameChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.vehicle_name)) },
        singleLine = true,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onSave, enabled = !state.isSaving) {
            Text(stringResource(R.string.save_vehicle))
        }
        Button(onClick = onRefresh, enabled = !isRefreshing) {
            Text(stringResource(R.string.restore_backup))
        }
    }
}

@Composable
private fun VehicleList(state: VehicleListUiState) {
    Column {
        Text(
            text = stringResource(R.string.saved_vehicles),
            style = MaterialTheme.typography.titleMedium,
        )
        if (state.vehicles.isEmpty()) {
            Text(stringResource(R.string.empty_vehicles))
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(state.vehicles, key = { vehicle -> vehicle.id }) { vehicle ->
                    Text(
                        text = vehicle.name,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SessionPhase.localizedLabel(): String =
    stringResource(
        when (this) {
            SessionPhase.UNKNOWN -> R.string.session_unknown
            SessionPhase.LOCAL -> R.string.session_local
            SessionPhase.ANONYMOUS -> R.string.session_anonymous
            SessionPhase.PERMANENT -> R.string.session_permanent
            SessionPhase.SIGNED_OUT -> R.string.session_signed_out
            SessionPhase.DELETING -> R.string.session_deleting
        },
    )

private const val DATABASE_FILE_NAME = "carapp.db"
