package com.linkshield.sandbox.ui.Upgrade

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshield.sandbox.license.LicenseManager

// Payment constants — edit ONLY here
private const val EASYPAISA_NUMBER = "03136176616"
private const val JAZZCASH_NUMBER = "03061934345"
private const val USDT_ADDRESS = "TQhUtaU9sg2hKfEM5FdeB3VGpzotKtwVub"
private const val PRICE_PKR = "350"
private const val PRICE_USD = "1.25"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeScreen(
    licenseManager: LicenseManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var keyInput by remember { mutableStateOf("") }
    var keyError by remember { mutableStateOf<String?>(null) }
    var isProUnlocked by remember { mutableStateOf(licenseManager.isProUser()) }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "Upgrade to LinkShield Pro",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "One-time payment. Lifetime access. All features unlocked.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Price pill
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Rs. $PRICE_PKR  /  $$PRICE_USD  (one-time)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Features Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pro Features Included",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                ProFeatureRow("Unlimited DoH Protection", "Encrypted DNS for all browsing sessions")
                Spacer(modifier = Modifier.height(8.dp))
                ProFeatureRow("Ad-Free YouTube Experience", "Automatic ad-blocking & background support")
                Spacer(modifier = Modifier.height(8.dp))
                ProFeatureRow("Isolated Media Grabber", "Download videos in high resolution safely")
                Spacer(modifier = Modifier.height(8.dp))
                ProFeatureRow("Unlimited Downloads", "No 20-download cap")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (isProUnlocked) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Pro License Active!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Thank you for supporting LinkShield Sandbox.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            // Payment Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Payment Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    PaymentRow("EasyPaisa", EASYPAISA_NUMBER, "Account: Salman Latif", context)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    PaymentRow("JazzCash", JAZZCASH_NUMBER, "Account: Salman Latif", context)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // USDT Crypto Payment
                    Column {
                        Text(
                            "USDT (TRC20)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                USDT_ADDRESS,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                                fontSize = 11.sp
                            )
                            IconButton(
                                onClick = { copyToClipboard(context, "USDT Address", USDT_ADDRESS) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy USDT address",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(
                            "TRC20 network only",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Activation Key Entry Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Activate License Key",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = {
                            keyInput = it
                            keyError = null
                        },
                        label = { Text("Enter License Key") },
                        isError = keyError != null,
                        supportingText = {
                            keyError?.let {
                                Text(text = it, color = MaterialTheme.colorScheme.error)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            val trimmedKey = keyInput.trim()
                            when {
                                trimmedKey.isBlank() -> keyError = "Please enter your license key"
                                licenseManager.validateKey(trimmedKey) -> {
                                    isProUnlocked = true
                                    keyError = null
                                    Toast.makeText(context, "Pro Activated! Enjoy unlimited features.", Toast.LENGTH_SHORT).show()
                                }
                                else -> keyError = "Invalid key or already used. Please contact support."
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Lock, null, Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Activate Pro Key", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun ProFeatureRow(title: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PaymentRow(label: String, value: String, subtitle: String, context: Context) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { copyToClipboard(context, label, value) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Default.ContentCopy,
            contentDescription = "Copy $label",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "$label copied to clipboard!", Toast.LENGTH_SHORT).show()
}
