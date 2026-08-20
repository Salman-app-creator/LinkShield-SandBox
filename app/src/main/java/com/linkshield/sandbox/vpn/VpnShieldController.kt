package com.linkshield.sandbox.vpn

// ─────────────────────────────────────────────────────────────────────────────
// VpnShieldController.kt
//
// Single Compose-friendly entry point for all VPN operations.
//
// Responsibilities:
//   1. Toggle connect / disconnect from UI (single coroutine-safe call)
//   2. Request VPN permission via ActivityResultLauncher before first connect
//   3. Show / update / cancel the foreground notification automatically
//   4. Expose StateFlow<WireGuardVpnStatus> to the UI
//
// Usage in Compose (example — actual UI wiring done separately):
//
//   val controller = remember { VpnShieldController(context) }
//   val status by controller.status.collectAsStateWithLifecycle()
//
//   val permLauncher = rememberLauncherForActivityResult(
//       StartActivityForResult()
//   ) { result ->
//       if (result.resultCode == RESULT_OK) {
//           scope.launch { controller.connect() }
//       }
//   }
//
//   Switch(
//       checked  = status == WireGuardVpnStatus.CONNECTED,
//       onCheckedChange = {
//           scope.launch { controller.toggle(activity, permLauncher) }
//       }
//   )
// ─────────────────────────────────────────────────────────────────────────────

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.wireguard.android.backend.GoBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class VpnShieldController(private val context: Context) {

    private val manager = WireGuardVpnManager(context.applicationContext)

    /** Observe this in Compose with collectAsStateWithLifecycle(). */
    val status: StateFlow<WireGuardVpnStatus> = manager.state.status

    val error:  StateFlow<String?> = manager.state.error

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Toggle VPN on/off.
     * Handles permission request automatically when needed.
     *
     * @param activity  needed to start the VPN permission Activity
     * @param launcher  ActivityResultLauncher registered in the composable
     *                  (call connect() in its callback if RESULT_OK)
     */
    suspend fun toggle(
        activity: Activity,
        launcher: ActivityResultLauncher<Intent>
    ) {
        when (status.value) {
            WireGuardVpnStatus.CONNECTED      -> disconnect()
            WireGuardVpnStatus.DISCONNECTED,
            WireGuardVpnStatus.ERROR          -> requestPermissionOrConnect(activity, launcher)
            else                             -> { /* ignore during transition */ }
        }
    }

    /** Connect with VPN permission already granted. */
    suspend fun connect() = withContext(Dispatchers.IO) {
        val result = manager.connect()
        result.onSuccess {
            VpnNotificationHelper.show(context, WireGuardVpnStatus.CONNECTED)
        }
        result
    }

    /** Disconnect and cancel the notification. */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        val result = manager.disconnect()
        VpnNotificationHelper.cancel(context)
        result
    }

    fun hasConfig(): Boolean = manager.hasConfiguration()

    fun clearError() = manager.clearError()

    // ── Private ───────────────────────────────────────────────────────────────

    private suspend fun requestPermissionOrConnect(
        activity: Activity,
        launcher: ActivityResultLauncher<Intent>
    ) = withContext(Dispatchers.Main) {
        runCatching {
            // prepare() returns null if permission already granted
            val permIntent = GoBackend.VpnService.prepare(activity)
            if (permIntent == null) {
                // Permission already granted — connect immediately
                withContext(Dispatchers.IO) { connect() }
            } else {
                // Launch system VPN permission dialog.
                // Caller must call connect() in the launcher's result callback.
                launcher.launch(permIntent)
            }
        }.onFailure { e ->
            manager.state.setError("VPN permission error: ${e.message}")
        }
    }
}
