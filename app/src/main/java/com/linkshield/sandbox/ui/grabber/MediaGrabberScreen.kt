package com.linkshield.sandbox.ui.grabber

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGrabberScreen(
    viewModel: MediaExtractorViewModel = viewModel()
) {
    val context = LocalContext.current
    val keyboardController =
        LocalSoftwareKeyboardController.current

    var inputUrl by remember {
        mutableStateOf("")
    }

    val extractionState by
        viewModel.state.collectAsStateCompat()

    fun processUrl(url: String) {
        val cleanUrl = url.trim()

        if (cleanUrl.isBlank()) {
            return
        }

        keyboardController?.hide()

        viewModel.extract(
            url = cleanUrl
        )
    }

    LaunchedEffect(extractionState.error) {
        extractionState.error?.let { message ->
            Toast.makeText(
                context,
                message,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(extractionState.selected) {
        val selected =
            extractionState.selected

        if (
            selected != null &&
            !extractionState.isLoading &&
            extractionState.error == null
        ) {
            val result =
                GrabberDownloadManager(
                    context
                ).download(selected)

            if (result != null) {
                Toast.makeText(
                    context,
                    "Download started...",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    context,
                    "Unable to start download.",
                    Toast.LENGTH_LONG
                ).show()
            }

            viewModel.clear()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement =
            Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        MaterialTheme.colorScheme
                            .surfaceVariant
                )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Media Downloader",
                    style =
                        MaterialTheme.typography
                            .titleMedium,
                    fontWeight =
                        FontWeight.Bold
                )

                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = {
                        inputUrl = it
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "Paste video or media link..."
                        )
                    },
                    singleLine = true,
                    shape =
                        RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (inputUrl.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    inputUrl = ""
                                    viewModel.clear()
                                }
                            ) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription =
                                        "Clear"
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    val clipboard =
                                        context.getSystemService(
                                            Context.CLIPBOARD_SERVICE
                                        ) as android.content.ClipboardManager

                                    val clipData =
                                        clipboard.primaryClip

                                    if (
                                        clipData != null &&
                                        clipData.itemCount > 0
                                    ) {
                                        val text =
                                            clipData
                                                .getItemAt(0)
                                                .text
                                                ?.toString()
                                                ?: ""

                                        if (
                                            text.isNotBlank()
                                        ) {
                                            inputUrl = text
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Outlined.ContentPaste,
                                    contentDescription =
                                        "Paste"
                                )
                            }
                        }
                    },
                    keyboardOptions =
                        KeyboardOptions(
                            imeAction =
                                ImeAction.Done
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onDone = {
                                processUrl(
                                    inputUrl
                                )
                            }
                        )
                )
                                Button(
                    onClick = {
                        processUrl(inputUrl)
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    enabled =
                        inputUrl.isNotBlank() &&
                            !extractionState.isLoading,
                    shape =
                        RoundedCornerShape(12.dp)
                ) {
                    if (
                        extractionState.isLoading
                    ) {
                        CircularProgressIndicator(
                            modifier =
                                Modifier.size(20.dp),
                            color =
                                MaterialTheme.colorScheme
                                    .onPrimary,
                            strokeWidth = 2.dp
                        )

                        Spacer(
                            modifier =
                                Modifier.width(8.dp)
                        )

                        Text(
                            "Extracting..."
                        )
                    } else {
                        Icon(
                            Icons.Default.Download,
                            contentDescription =
                                null
                        )

                        Spacer(
                            modifier =
                                Modifier.width(8.dp)
                        )

                        Text(
                            "Fetch & Download"
                        )
                    }
                }
            }
        }
    }
}

/*
 * Small compatibility helper.
 *
 * Keeps the screen independent from lifecycle-compose APIs.
 */
@Composable
private fun <T> kotlinx.coroutines.flow.StateFlow<T>
    .collectAsStateCompat():
    androidx.compose.runtime.State<T> {
    return androidx.compose.runtime.collectAsState(
        this
    )
    }
