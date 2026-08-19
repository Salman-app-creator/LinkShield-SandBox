package com.linkshield.sandbox.ui.grabber

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun GrabberScreen(
    viewModel: GrabberViewModel? = null,
    onBack: () -> Unit,
    onDownload: (MediaQualityOption) -> Unit
) {
    var url by rememberSaveable { mutableStateOf("") }
    var audioOnly by rememberSaveable { mutableStateOf(false) }
    var highQuality by rememberSaveable { mutableStateOf(true) }
    var ready by rememberSaveable { mutableStateOf(false) }
    BackHandler { onBack() }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text("Grabber", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("[ 20 Free Downloads Remaining ]", fontWeight = FontWeight.Bold)
                Text("Upgrade to Pro for Unlimited", style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedTextField(url, { url = it; ready = false }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Paste or Fetch Link...") })
        Card(Modifier.fillMaxWidth().height(170.dp), shape = RoundedCornerShape(14.dp)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PlayCircle, null, Modifier.size(50.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(if (ready) "Demo media preview" else "Media Preview Area")
                }
            }
        }
        Text("Options:", fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(audioOnly, { audioOnly = it }); Text("Audio Only")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(highQuality, { highQuality = it }); Text("High Qual")
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { if (!ready) ready = true else onDownload(if (audioOnly) MediaQualityOption("MP3", "Audio") else MediaQualityOption("MP4", if (highQuality) "1080p" else "720p")) },
            enabled = url.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text(if (ready) "Download" else "Fetch Media")
        }
    }
}
