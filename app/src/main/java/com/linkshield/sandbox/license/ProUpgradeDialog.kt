package com.linkshield.sandbox.license

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private const val EASYPAISA_NUMBER = "03136176616"
private const val JAZZCASH_NUMBER = "03061934345"
private const val EASYPAISA_TITLE = "Salman Latif"
private const val JAZZCASH_TITLE = "Salman Latif"
private const val USDT_ADDRESS = "TQhUtaU9sg2hKfEM5FdeB3VGpzotKtwVub"

@Composable
fun ProUpgradeDialog(
    licenseManager: LicenseManager? = null,
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    var key by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.94f),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                Modifier.padding(20.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Upgrade to LinkShield Pro", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("UI-only purchase screen. License validation remains isolated for the backend phase.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PaymentLine("EasyPaisa", EASYPAISA_NUMBER, EASYPAISA_TITLE, context)
                PaymentLine("JazzCash", JAZZCASH_NUMBER, JAZZCASH_TITLE, context)
                PaymentLine("USDT (TRC20)", USDT_ADDRESS, "TRC20 only", context)
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.uppercase().take(32); status = null },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Pro License Key") },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    shape = RoundedCornerShape(12.dp)
                )
                Button(
                    onClick = {
                        status = if (key.isBlank()) "Enter a license key." else "License validation will be connected in the backend phase."
                    },
                    enabled = key.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Activate Pro") }
                status?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Maybe Later") }
            }
        }
    }
}

@Composable
private fun PaymentLine(label: String, value: String, subtitle: String, context: Context) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Bold)
            Text(value, style = MaterialTheme.typography.bodySmall)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
            Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
        }) { Icon(Icons.Default.ContentCopy, "Copy $label") }
    }
}
