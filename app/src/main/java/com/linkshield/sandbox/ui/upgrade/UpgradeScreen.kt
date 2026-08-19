package com.linkshield.sandbox.ui.upgrade

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshield.sandbox.R
import com.linkshield.sandbox.license.LicenseManager

@Composable
fun UpgradeScreen(
    licenseManager: LicenseManager? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var key by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_app_logo),
                contentDescription = "LinkShield Sandbox",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("LinkShield Pro", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Unlimited Grabber downloads", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Pro benefits", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                ProFeature("Unlimited media download access")
                ProFeature("Higher-quality download choices")
                ProFeature("Priority access to future engine integrations")
                ProFeature("No free-download counter")
            }
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Payment Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                PaymentRow("EasyPaisa", "03136176616", "Salman Latif", context)
                HorizontalDivider()
                PaymentRow("JazzCash", "03061934345", "Salman Latif", context)
                HorizontalDivider()
                PaymentRow("USDT (TRC20)", "TQhUtaU9sg2hKfEM5FdeB3VGpzotKtwVub", "TRC20 only", context)
                Text("Price: Rs. 350 / $1.25", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Activate Pro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("UI-only activation form. License validation will be connected in the backend phase.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.uppercase().take(32); message = null },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Pro License Key") },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    shape = RoundedCornerShape(12.dp)
                )
                Button(
                    onClick = { message = if (key.isBlank()) "Enter your license key." else "Activation will be enabled with the license engine in the next phase." },
                    enabled = key.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Activate Pro") }
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) }
            }
        }
        Spacer(Modifier.height(36.dp))
    }
}

@Composable
private fun ProFeature(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CheckCircle, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
private fun PaymentRow(label: String, value: String, subtitle: String, context: Context) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Bold)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
            Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
        }) { Icon(Icons.Default.ContentCopy, "Copy $label") }
    }
}
