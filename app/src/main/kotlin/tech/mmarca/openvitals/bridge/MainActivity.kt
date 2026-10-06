package tech.mmarca.openvitals.bridge

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import tech.mmarca.openvitals.bridge.openvitals.OpenVitalsApi
import tech.mmarca.openvitals.bridge.ui.BridgeActions
import tech.mmarca.openvitals.bridge.ui.BridgeScreen
import tech.mmarca.openvitals.bridge.ui.BridgeViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val viewModel: BridgeViewModel = viewModel()
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    // Android's own dialog, one per group. Whatever the answer, read the grants again.
                    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                        viewModel.refresh()
                    }
                    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
                    Box(modifier = Modifier.safeDrawingPadding()) {
                        BridgeScreen(
                            state = state,
                            actions = BridgeActions(
                                onSelectBuild = viewModel::selectBuild,
                                onAskAll = { viewModel.missingPermissions().takeIf { it.isNotEmpty() }?.let(ask::launch) },
                                onAsk = { group -> ask.launch(viewModel.permissionFor(group)) },
                                onRead = viewModel::readGroup,
                                onReadAll = viewModel::readAll,
                                onOpenSharingSettings = { openSharingSettings(state.build?.packageName) },
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun openSharingSettings(packageName: String?) {
        val intent = Intent(OpenVitalsApi.ACTION_MANAGE_DATA_SHARING).apply { packageName?.let(::setPackage) }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "This OpenVitals has no sharing settings.", Toast.LENGTH_SHORT).show()
        }
    }
}
