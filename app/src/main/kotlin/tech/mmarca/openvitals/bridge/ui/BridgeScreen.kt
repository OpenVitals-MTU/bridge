package tech.mmarca.openvitals.bridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import tech.mmarca.openvitals.bridge.openvitals.OpenVitalsBuild
import tech.mmarca.openvitals.bridge.openvitals.OpenVitalsGroup
import tech.mmarca.openvitals.bridge.openvitals.ReadResult

/** What the screen can ask its host to do. */
class BridgeActions(
    val onSelectBuild: (OpenVitalsBuild) -> Unit,
    val onAskAll: () -> Unit,
    val onAsk: (OpenVitalsGroup) -> Unit,
    val onRead: (OpenVitalsGroup) -> Unit,
    val onReadAll: () -> Unit,
    val onOpenSharingSettings: () -> Unit,
)

@Composable
fun BridgeScreen(state: BridgeUiState, actions: BridgeActions) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item { Header(state, actions) }
        if (state.build != null) {
            items(state.groups, key = { it.group.name }) { group -> GroupCard(group, actions) }
        }
    }
}

@Composable
private fun Header(state: BridgeUiState, actions: BridgeActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("OpenVitals Bridge", style = MaterialTheme.typography.headlineSmall)
        if (state.build == null) {
            Text("No OpenVitals with the data sharing API is installed. Install a debug build of OpenVitals, then come back.")
            return@Column
        }
        if (state.builds.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.builds.forEach { build ->
                    FilterChip(
                        selected = build == state.build,
                        onClick = { actions.onSelectBuild(build) },
                        label = { Text(build.name.lowercase()) },
                    )
                }
            }
        }
        Text("Reading from ${state.build.packageName}")
        Text("Sharing status: ${state.sharingStatus ?: "…"}  ·  API version ${state.apiVersion ?: "?"}")
        if (state.sharingStatus == "sharing_off") {
            Text("Sharing is off in OpenVitals. Turn it on there first.", color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = actions.onAskAll) { Text("Ask for all groups") }
            OutlinedButton(onClick = actions.onReadAll, enabled = !state.busy) { Text("Read everything") }
        }
        TextButton(onClick = actions.onOpenSharingSettings) { Text("Open OpenVitals sharing settings") }
        if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun GroupCard(state: GroupState, actions: BridgeActions) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.group.path, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(if (state.granted) "allowed" else "not allowed")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!state.granted) OutlinedButton(onClick = { actions.onAsk(state.group) }) { Text("Ask") }
                OutlinedButton(onClick = { actions.onRead(state.group) }) { Text("Read") }
            }
            state.reading?.results?.forEach { (label, result) -> ResultLine(label, result) }
        }
    }
}

/** One table's outcome: its row count and status, and the first rows as text. */
@Composable
private fun ResultLine(label: String, result: ReadResult) {
    val summary = when (result) {
        is ReadResult.Read -> buildString {
            append("$label: ${result.table.rows.size} rows, ${result.table.status ?: "no status"}")
            result.table.historyStart?.let { append(", history from $it") }
        }
        is ReadResult.Denied -> "$label: denied (${result.message})"
        is ReadResult.Failed -> "$label: failed (${result.message})"
        ReadResult.Unavailable -> "$label: unavailable"
    }
    Text(summary, style = MaterialTheme.typography.bodyMedium)
    if (result is ReadResult.Read) {
        result.table.rows.take(PreviewRows).forEach { row ->
            Text(
                result.table.columns.zip(row).joinToString(" ") { (name, value) -> "$name=$value" },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

private const val PreviewRows = 3
