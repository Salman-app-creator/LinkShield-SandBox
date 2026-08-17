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
import com.linkshield.sandbox.api.CobaltApiService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGrabberScreen(
    viewModel: MediaExtractorViewModel = viewModel()
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var inputUrl by remember { mutableStateOf("") }

    val extractionState by viewModel.extractionState.collectAsState()

    fun processUrl(url: String) {
        if (url.isBlank()) return
        keyboardController?.hide()
        val request = CobaltApiService.CobaltRequest(
            url = url,
            videoQuality = "720",
            audioFormat = "mp3",
            downloadMode = "auto"
        )
        viewModel.extractMedia(request)
    }

    LaunchedEffect(extractionState) {
        when (val state = extractionState) {
            is MediaExtractorViewModel.ExtractionState.Success -> {
                val result = state.data
                if (result.status == "stream" || result.status == "redirect" || result.status == "tunnel") {
                    val downloadUrl = result.url ?: ""
                    if (downloadUrl.isNotEmpty()) {
                        val filename = result.filename ?: "download_${System.currentTimeMillis()}"
                        viewModel.startDownload(
                            context = context,
                            url = downloadUrl,
                            filename = filename
                        )
                        Toast.makeText(context, "Download started...", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            is MediaExtractorViewModel.ExtractionState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        } else {
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clipData = clipboard.primaryClip
                                if (clipData != null && clipData.itemCount > 0) {
                                    val text = clipData.getItemAt(0).text?.toString() ?: ""
                                    if (text.isNotEmpty()) {
                                        inputUrl = text
                                    }
                                }
                            }) {
                                Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { processUrl(inputUrl) })
                )

                Button(
                    onClick = { processUrl(inputUrl) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = inputUrl.isNotBlank() && extractionState !is MediaExtractorViewModel.ExtractionState.Loading,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (extractionState is MediaExtractorViewModel.ExtractionState.Loading) {
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
    }
}
