package com.linkshield.sandbox.ui.grabber

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
// FIX #3: Removed dead import – CobaltApiService has no nested CobaltRequest class
// and cannot be used as a static object companion. Import deleted entirely.
// import com.linkshield.sandbox.api.CobaltApiService  ← DELETED

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGrabberScreen(
    viewModel: MediaExtractorViewModel = viewModel()
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var inputUrl by remember { mutableStateOf("") }

    // FIX #2: Was "viewModel.extractionState" (property does not exist).
    // MediaExtractorViewModel exposes "val state: StateFlow<MediaExtractorState>".
    val state by viewModel.state.collectAsState()

    // FIX #5 + #6: Removed the entire broken LaunchedEffect block that referenced:
    //   – MediaExtractorViewModel.ExtractionState.Success / .Error / .Loading  (sealed class doesn't exist)
    //   – state.data.status / state.data.url / state.data.filename              (fields don't exist)
    // Replaced with a simple error-toast effect keyed on state.error (which does exist).
    LaunchedEffect(state.error) {
        state.error?.let { errorMessage ->
            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
        }
    }

    // FIX #4: Was "CobaltApiService.CobaltRequest(...) + viewModel.extractMedia(request)".
    // MediaExtractorViewModel only exposes fun extract(url: String, title: String = "").
    fun processUrl(url: String) {
        if (url.isBlank()) return
        keyboardController?.hide()
        viewModel.extract(url = url)
    }

    // FIX #7: Was "viewModel.startDownload(context, url, filename)" – method doesn't exist
    // on MediaExtractorViewModel. Delegating to GrabberDownloadManager (same package,
    // already used by GrabberScreen) via state.selected keeps the exact same download
    // pipeline the rest of the grabber UI uses.
    fun downloadSelected() {
        val option = state.selected ?: return
        val manager = GrabberDownloadManager(context.applicationContext)
        val result = manager.download(option)
        if (result != null) {
            Toast.makeText(context, "Download started...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Download failed – invalid URL.", Toast.LENGTH_LONG).show()
        }
    }

    // ── UI ─────────────────────────────────────────────────────────────────────
    // Layout is identical to original. Only the wiring to the ViewModel changed.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ── URL input card ────────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Media Downloader",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Paste video or media link...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (inputUrl.isNotEmpty()) {
                            IconButton(onClick = { inputUrl = "" }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Clear"
                                )
                            }
                        } else {
                            IconButton(onClick = {
                                val clipboard = context
                                    .getSystemService(Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                                val clip = clipboard.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    val text = clip.getItemAt(0).text?.toString() ?: ""
                                    if (text.isNotEmpty()) inputUrl = text
                                }
                            }) {
                                Icon(
                                    Icons.Outlined.ContentPaste,
                                    contentDescription = "Paste"
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { processUrl(inputUrl) }
                    )
                )

                Button(
                    onClick = { processUrl(inputUrl) },
                    modifier = Modifier.fillMaxWidth(),
                    // FIX #5: Was "extractionState !is MediaExtractorViewModel.ExtractionState.Loading"
                    enabled = inputUrl.isNotBlank() && !state.isLoading,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    // FIX #5: Was "extractionState is MediaExtractorViewModel.ExtractionState.Loading"
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Extracting...")
                    } else {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Fetch & Download")
                    }
                }
            }
        }

        // ── Format picker card (shown after successful extraction) ─────────────
        // This section was implied by the original code's download logic but was
        // never rendered because the ViewModel bridge was broken. Now it correctly
        // reads state.options and state.selected that MediaExtractorViewModel provides.
        if (state.options.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Available Formats",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    state.options.forEach { option ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.selected?.url == option.url,
                                onClick = { viewModel.select(option) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.displayLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                if (option.mimeType.isNotBlank()) {
                                    Text(
                                        text = option.mimeType,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // FIX #7 applied here: download goes through GrabberDownloadManager,
                    // not the non-existent viewModel.startDownload().
                    Button(
                        onClick = { downloadSelected() },
                        enabled = state.selected != null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download Selected")
                    }
                }
            }
        }
    }
}
