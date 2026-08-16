package com.linkshield.sandbox.ui.license

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.linkshield.sandbox.license.LicenseManager
import java.net.URLEncoder

private const val WHATSAPP_NUMBER = "923136176616"
private const val EASYPAISA_NUMBER = "03136176616"
private const val EASYPAISA_TITLE = "Salman Latif"
private const val JAZZCASH_NUMBER = "03061934345"
private const val JAZZCASH_TITLE = "Salman Latif"
private const val USDT_ADDRESS =
    "TQhUtaU9sg2hKfEM5FdeB3VGpzotKtwVub"
private const val PRICE_PKR = "350"
private const val PRICE_USD = "1.25"

@Composable
fun ProUpgradeDialog(
    licenseManager: LicenseManager,
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    var keyInput by remember { mutableStateOf("") }
    var keyError by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(0.95f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (success) {
                    Icon(
                        Icons.Default.CheckCircle,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(60.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Pro Unlocked! 🎉",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Your LinkShield Pro features are now active.",
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = onUnlocked,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Continue")
                    }
                } else {
                    Icon(
                        Icons.Default.Star,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Upgrade to LinkShield Pro",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Browse, Check & Grab Safely — with more powerful tools.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor =
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            FeatureRow("🛡️", "Advanced suspicious-link detection")
                            FeatureRow("🌐", "Sandbox browser")
                            FeatureRow("🎵", "Advanced media detection")
                            FeatureRow("📥", "More media download options")
                            FeatureRow("🔗", "Advanced URL expansion")
                            FeatureRow("📸", "URL preview & snapshot tools")
                            FeatureRow("🌍", "Network & IP information")
                            FeatureRow("🔒", "Secure Network transport")
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Rs. $PRICE_PKR / $$PRICE_USD • One-time payment",
                            modifier = Modifier.padding(10.dp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor =
                                MaterialTheme.colorScheme.surfaceVariant
                                    .copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PaymentRow(
                                "Easypaisa",
                                EASYPAISA_NUMBER,
                                "Account: $EASYPAISA_TITLE",
                                context
                            )
                            HorizontalDivider(thickness = 0.5.dp)
                            PaymentRow(
                                "JazzCash",
                                JAZZCASH_NUMBER,
                                "Account: $JAZZCASH_TITLE",
                                context
                            )
                            HorizontalDivider(thickness = 0.5.dp)
                            Text(
                                "Crypto / USDT (TRC20)",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    USDT_ADDRESS,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 10.sp
                                )
                                IconButton(
                                    onClick = {
                                        copyToClipboard(
                                            context,
                                            "USDT Address",
                                            USDT_ADDRESS
                                        )
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        "Copy USDT address",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Text(
                                "⚠ TRC20 network only",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    Button(
                        onClick = {
                            val message =
                                "Hi, I want to buy LinkShield Pro. I have made the payment."
                            val url =
                                "https://wa.me/$WHATSAPP_NUMBER?text=${
                                    URLEncoder.encode(message, "UTF-8")
                                }"
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("💬 Chat on WhatsApp")
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))

                    Text(
                        "Enter your Pro License Key after payment:",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = {
                            keyInput = it.uppercase().take(20)
                            keyError = null
                        },
                        label = { Text("Pro License Key") },
                        placeholder = { Text("LSHD-XXXX-XXXX-CCCC") },
                        singleLine = true,
                        isError = keyError != null,
                        supportingText = {
                            keyError?.let {
                                Text(
                                    it,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.sp
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            capitalization =
                                KeyboardCapitalization.Characters
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(Modifier.height(10.dp))

                    Button(
                        onClick = {
                            when {
                                keyInput.isBlank() ->
                                    keyError = "Please enter your license key"

                                licenseManager.validateKey(keyInput.trim()) ->
                                    success = true

                                else ->
                                    keyError =
                                        "Invalid or already used on another device. Contact support."
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Activate Pro")
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Maybe Later",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: String,
    text: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            icon,
            modifier = Modifier.width(28.dp),
            fontSize = 17.sp
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PaymentRow(
    label: String,
    value: String,
    subtitle: String,
    context: Context
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                copyToClipboard(context, label, value)
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            Icons.Default.ContentCopy,
            "Copy $label",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun copyToClipboard(
    context: Context,
    label: String,
    text: String
) {
    val manager =
        context.getSystemService(
            Context.CLIPBOARD_SERVICE
        ) as ClipboardManager

    manager.setPrimaryClip(
        ClipData.newPlainText(label, text)
    )

    Toast.makeText(
        context,
        "$label copied!",
        Toast.LENGTH_SHORT
    ).show()
}
