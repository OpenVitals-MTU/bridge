package tech.mmarca.openvitals.bridge.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tech.mmarca.openvitals.bridge.openvitals.GroupReading
import tech.mmarca.openvitals.bridge.openvitals.OpenVitalsBuild
import tech.mmarca.openvitals.bridge.openvitals.OpenVitalsGroup
import tech.mmarca.openvitals.bridge.openvitals.OpenVitalsPaths
import tech.mmarca.openvitals.bridge.openvitals.OpenVitalsReader
import tech.mmarca.openvitals.bridge.openvitals.ReadResult

/** A group as `/groups` and Android report it. */
data class GroupState(
    val group: OpenVitalsGroup,
    val granted: Boolean,
    val reading: GroupReading? = null,
)

data class BridgeUiState(
    val builds: List<OpenVitalsBuild> = emptyList(),
    val build: OpenVitalsBuild? = null,
    /** `/groups`' status: why data may be missing, for example `sharing_off`. */
    val sharingStatus: String? = null,
    val apiVersion: Long? = null,
    val groups: List<GroupState> = OpenVitalsGroup.entries.map { GroupState(it, granted = false) },
    val busy: Boolean = false,
)

class BridgeViewModel(application: Application) : AndroidViewModel(application) {
    private val reader = OpenVitalsReader(application)

    private val _state = MutableStateFlow(BridgeUiState())
    val state: StateFlow<BridgeUiState> = _state.asStateFlow()

    /** On start and on resume: the user may have changed a grant or the sharing switch. */
    fun refresh() {
        val builds = reader.installedBuilds()
        val build = _state.value.build?.takeIf { it in builds } ?: builds.firstOrNull()
        _state.update { it.copy(builds = builds, build = build) }
        if (build == null) return
        viewModelScope.launch {
            val groups = reader.read(build, OpenVitalsPaths.GROUPS)
            val table = (groups as? ReadResult.Read)?.table
            val firstRow = table?.rows?.firstOrNull()
            _state.update { state ->
                state.copy(
                    sharingStatus = table?.status ?: describe(groups),
                    apiVersion = firstRow?.let { table.column(it, "api_version") as? Long },
                    groups = state.groups.map { it.copy(granted = reader.isGranted(build, it.group)) },
                )
            }
        }
    }

    fun selectBuild(build: OpenVitalsBuild) {
        _state.update { it.copy(build = build, groups = it.groups.map { group -> group.copy(reading = null) }) }
        refresh()
    }

    /** The permissions to ask Android for: every group not yet allowed. */
    fun missingPermissions(): Array<String> {
        val build = _state.value.build ?: return emptyArray()
        return reader.permissions(build, _state.value.groups.filterNot { it.granted }.map { it.group })
    }

    fun permissionFor(group: OpenVitalsGroup): Array<String> {
        val build = _state.value.build ?: return emptyArray()
        return reader.permissions(build, listOf(group))
    }

    fun readGroup(group: OpenVitalsGroup) = launchRead { build ->
        val reading = reader.readGroup(build, group)
        _state.update { state -> state.copy(groups = state.groups.map { if (it.group == group) it.copy(reading = reading) else it }) }
    }

    /** Reads every group: the ones not allowed come back as denied. */
    fun readAll() = launchRead { build ->
        val readings = reader.readAll(build).associateBy { it.group }
        _state.update { state -> state.copy(groups = state.groups.map { it.copy(reading = readings[it.group]) }) }
    }

    private fun launchRead(block: suspend (OpenVitalsBuild) -> Unit) {
        val build = _state.value.build ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                block(build)
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun describe(result: ReadResult): String = when (result) {
        is ReadResult.Read -> result.table.status ?: "ok"
        is ReadResult.Denied -> "denied"
        is ReadResult.Failed -> "failed: ${result.message}"
        ReadResult.Unavailable -> "unavailable"
    }
}
