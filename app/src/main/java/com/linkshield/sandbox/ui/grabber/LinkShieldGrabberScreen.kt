package com.linkshield.sandbox.ui.grabber

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LinkShieldGrabberScreen(
    onBackToBrowser: () -> Unit,
    onUpgradeClick: () -> Unit
) {
    val context = LocalContext.current
    var inputUrl by rememberSaveable { mutableStateOf("") }
    var audioOnly by rememberSaveable { mutableStateOf(false) }
    var highQuality by rememberSaveable { mutableStateOf(true) }
    var fetched by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackToBrowser, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.ArrowBack, "Back to current website")
            }
            Text("Grabber", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("[ 20 Free Downloads Remaining ]", fontWeight = FontWeight.Bold)
                    Text("Upgrade to Pro for Unlimited", fontSize = 12.sp)
                }
                TextButton(onClick = onUpgradeClick) {
                    Icon(Icons.Default.Upgrade, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Upgrade")
                }
            }
        }

        OutlinedTextField(
            value = inputUrl,
            onValueChange = { inputUrl = it; fetched = false },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Paste or Fetch Link...") },
            leadingIcon = { Icon(Icons.Default.Link, null) },
            trailingIcon = {
                IconButton(onClick = { inputUrl = readClipboard(context) }) {
                    Icon(Icons.Default.ContentPaste, "Paste")
                }
            },
            shape = RoundedCornerShape(12.dp)
        )

        Card(
            Modifier.fillMaxWidth().height(180.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PlayCircle, null, Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(if (fetched) "Demo media preview" else "Media Preview Area", fontWeight = FontWeight.SemiBold)
                    if (fetched) Text("Quality options ready", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Text("Options:", fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(audioOnly, { audioOnly = it })
                Text("Audio Only", fontSize = 13.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(highQuality, { highQuality = it })
                Text("High Qual", fontSize = 13.sp)
            }
        }

        if (fetched) {
            Text("Quality: ${if (highQuality) "1080p" else "720p"}${if (audioOnly) " • MP3" else " • MP4"}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = {
                if (!fetched) fetched = true
                else Toast.makeText(context, "Download engine will be integrated in the next backend phase.", Toast.LENGTH_SHORT).show()
            },
            enabled = inputUrl.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Download, null)
            Spacer(Modifier.width(8.dp))
            Text(if (fetched) "Download" else "Fetch Media", fontWeight = FontWeight.Bold)
        }
    }
}

private fun readClipboard(context: Context): String {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    return clipboard.primaryClip?.let { clip -> if (clip.itemCount > 0) clip.getItemAt(0).text?.toString().orEmpty() else "" }.orEmpty()
}
