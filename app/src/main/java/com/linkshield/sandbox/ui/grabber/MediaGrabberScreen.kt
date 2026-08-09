package com.linkshield.sandbox.ui.grabber

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.linkshield.sandbox.api.CobaltApiService
import com.linkshield.sandbox.license.LicenseManager
import com.linkshield.sandbox.ui.license.ProUpgradeDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class GrabberUiState {
    object Idle : GrabberUiState()
    object Loading : GrabberUiState()
    data class Ready(val directUrl: String, val filename: String) : GrabberUiState()
    data class Error(val msg: String) : GrabberUiState()
    data class Downloading(val msg: String) : GrabberUiState()
}

class MediaGrabberViewModel : androidx.lifecycle.ViewModel() {
    private val cobalt = CobaltApiService()

    private val _uiState = MutableStateFlow<GrabberUiState>(GrabberUiState.Idle)
    val uiState: StateFlow<GrabberUiState> = _uiState

    fun resolve(url: String) {
        _uiState.value = GrabberUiState.Loading
        viewModelScope.launch {
            val result = cobalt.fetchMediaUrl(url)
            result.onSuccess {
                val name = "linkshield_${System.currentTimeMillis()}.mp4"
                _uiState.value = GrabberUiState.Ready(it, name)
            }.onFailure {
                _uiState.value = GrabberUiState.Error(it.message ?: "Failed")
            }
        }
    }

    fun download(context: Context, url: String, fileName: String, licenseManager: LicenseManager) {
        if (!licenseManager.canDownload()) {
            _uiState.value = GrabberUiState.Error("LIMIT_REACHED")
            return
        }
        viewModelScope.launch {
            try {
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setTitle(fileName)
                    setDescription("LinkShield Download")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "LinkShield/$fileName")
                    setAllowedOverMetered(true)
                }
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                licenseManager.incrementDownload()
                _uiState.value = GrabberUiState.Downloading("Saved to Downloads/LinkShield/")
            } catch (e: Exception) {
                _uiState.value = GrabberUiState.Error(e.message ?: "Download failed")
            }
        }
    }

    fun reset() { _uiState.value = GrabberUiState.Idle }
}

@Composable
fun MediaGrabberScreen() {
    val context = LocalContext.current
    val vm: MediaGrabberViewModel = viewModel()
    val licenseManager = remember { LicenseManager(context) }

    val uiState by vm.uiState.collectAsState()
    var inputUrl by remember { mutableStateOf("") }
    var showProDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Media Grabber", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Paste a social media link to fetch the direct media file.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = inputUrl,
            onValueChange = { inputUrl = it; vm.reset() },
            label = { Text("Paste link (YouTube, TikTok, Instagram, Twitter...)") },
            leadingIcon = { Icon(Icons.Default.Link, null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { if (inputUrl.isNotBlank()) vm.resolve(inputUrl) },
            modifier = Modifier.fillMaxWidth(),
            enabled = inputUrl.isNotBlank() && uiState !is GrabberUiState.Loading
        ) {
            if (uiState is GrabberUiState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Fetching...")
            } else {
                Text("Fetch Media")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (val state = uiState) {
            is GrabberUiState.Ready -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Media Ready", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text(state.filename, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (!licenseManager.canDownload()) {
                                    showProDialog = true
                                } else {
                                    vm.download(context, state.directUrl, state.filename, licenseManager)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Download, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Download")
                        }
                        if (!licenseManager.isProUser()) {
                            Text(
                                "Free downloads used: ${licenseManager.getDownloadCount()}/20",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
            is GrabberUiState.Downloading -> {
                Text(state.msg, color = MaterialTheme.colorScheme.secondary)
            }
            is GrabberUiState.Error -> {
                if (state.msg == "LIMIT_REACHED") {
                    showProDialog = true
                } else {
                    Text(state.msg, color = MaterialTheme.colorScheme.error)
                }
            }
            else -> {}
        }

        Spacer(modifier = Modifier.weight(1f))

        Text("Or download direct file links (.mp4, .pdf, .zip)", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))
        var directUrl by remember { mutableStateOf("") }
        OutlinedTextField(
            value = directUrl,
            onValueChange = { directUrl = it },
            label = { Text("Direct file URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Button(
            onClick = {
                if (directUrl.isNotBlank()) {
                    if (!licenseManager.canDownload()) {
                        showProDialog = true
                    } else {
                        val name = directUrl.substringAfterLast("/").takeIf { it.isNotBlank() } ?: "file_${System.currentTimeMillis()}"
                        vm.download(context, directUrl, name, licenseManager)
                        directUrl = ""
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text("Download Direct Link")
        }
    }

    if (showProDialog) {
        ProUpgradeDialog(
            licenseManager = licenseManager,
            onDismiss = { showProDialog = false },
            onUnlocked = { showProDialog = false }
        )
    }
}
