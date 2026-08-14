package com.linkshield.sandbox.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun UpgradeScreen(
    remainingDays: Int = 28,
    isPro: Boolean = false,
    onActivateLicense: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var licenseKeyInput by remember { mutableStateOf("") }

    val easypaisaTitle = "Your EasyPaisa Title"
    val easypaisaNumber = "03001234567"

    val jazzcashTitle = "Your JazzCash Title"
    val jazzcashNumber = "03007654321"

    val usdtNetwork = "Network: TRC20 (Tron)"
    val usdtAddress = "TYaX9876543210UsdtWalletAddressHere"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isPro) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = if (isPro) "PRO Account Active" else "Free Trial Active",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isPro) "Unlimited Access" else "Remaining: $remainingDays Days Left",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = if (isPro) MaterialTheme.colorScheme.primary else Color(0xFFFFA500)
                )
            }
        }

        Text(
            text = "Upgrade to Pro (Manual Payment)",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        PaymentAccountCard("EasyPaisa", "Title: $easypaisaTitle", easypaisaNumber, Color(0xFF00A859)) {
            copyToClipboard(context, easypaisaNumber, "EasyPaisa Number")
        }

        PaymentAccountCard("JazzCash", "Title: $jazzcashTitle", jazzcashNumber, Color(0xFFD32F2F)) {
            copyToClipboard(context, jazzcashNumber, "JazzCash Number")
        }

        PaymentAccountCard("USDT (TRC20)", usdtNetwork, usdtAddress, Color(0xFF26A17B)) {
            copyToClipboard(context, usdtAddress, "USDT Address")
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(text = "Activate License Key", fontSize = 15.sp, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = licenseKeyInput,
            onValueChange = { licenseKeyInput = it },
            label = { Text("Enter License Key") },
            placeholder = { Text("XXXX-XXXX-XXXX-XXXX") },
            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                if (licenseKeyInput.isNotBlank()) onActivateLicense(licenseKeyInput.trim())
            },
            enabled = !isPro,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(if (isPro) "Already Upgraded" else "Activate License", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PaymentAccountCard(
    providerName: String,
    accountTitle: String,
    accountNumber: String,
    badgeColor: Color,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Surface(color = badgeColor, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        text = providerName,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = accountTitle, fontSize = 12.sp)
                Text(text = accountNumber, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            IconButton(
                onClick = onCopy,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp))
                    .size(40.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String, label: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied!", Toast.LENGTH_SHORT).show()
}
