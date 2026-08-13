package com.linkshield.sandbox.ui.license

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

// ─────────────────────────────────────────────────────────────────────────────
// Payment constants — edit ONLY here, reflects across the entire dialog
// ─────────────────────────────────────────────────────────────────────────────
private const val WHATSAPP_NUMBER  = "923136176616"
private const val EASYPAISA_NUMBER = "03136176616"
private const val EASYPAISA_TITLE  = "Salman Latif"
private const val JAZZCASH_NUMBER  = "03061934345"
private const val JAZZCASH_TITLE   = "Salman Latif"
private const val USDT_ADDRESS     = "TQhUtaU9sg2hKfEM5FdeB3VGpzotKtwVub"
private const val PRICE_PKR        = "350"
private const val PRICE_USD        = "1.25"

@Composable
fun ProUpgradeDialog(
    licenseManager: LicenseManager,
    onDismiss:      () -> Unit,
    onUnlocked:     () -> Unit
) {
    val context  = LocalContext.current
    var keyInput by remember { mutableStateOf("") }
    var keyError by remember { mutableStateOf<String?>(null) }
    var success  by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape    = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight(),
            colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (success) {
                    // ── Success state ─────────────────────────────────────────
                    Icon(
                        imageVector        = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.primary,
                        modifier           = Modifier.size(60.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Pro Unlocked! 🎉",
                        style      = MaterialTheme.typography.headlineSmall,
                        color      = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Unlimited downloads, full DNS Shield bypass, and all future features are now active.",
                        style     = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick  = onUnlocked,
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp)
                    ) {
                        Text("Continue", style = MaterialTheme.typography.labelLarge)
                    }

                } else {
                    // ── Upgrade state ─────────────────────────────────────────
                    Icon(
                        imageVector        = Icons.Default.Star,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.primary,
                        modifier           = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Upgrade to LinkShield Pro",
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign  = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "One-time payment · Unlimited downloads · Full DNS Shield · All future updates",
                        style     = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(14.dp))

                    // ── Payment card ──────────────────────────────────────────
                    Card(
                        shape  = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier            = Modifier.padding(16.dp),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
                        ) {
                            // Price pill
                            Surface(
                                shape    = RoundedCornerShape(10.dp),
                                color    = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Rs. $PRICE_PKR  /  \$$PRICE_USD  (one-time)",
                                    style      = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color      = MaterialTheme.colorScheme.primary,
                                    textAlign  = TextAlign.Center,
                                    modifier   = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
                                )
                            }

                            // Easypaisa
                            PaymentRow(
                                label    = "Easypaisa",
                                value    = EASYPAISA_NUMBER,
                                subtitle = "Account: $EASYPAISA_TITLE",
                                context  = context
                            )
                            HorizontalDivider(thickness = 0.5.dp)

                            // JazzCash
                            PaymentRow(
                                label    = "JazzCash",
                                value    = JAZZCASH_NUMBER,
                                subtitle = "Account: $JAZZCASH_TITLE",
                                context  = context
                            )
                            HorizontalDivider(thickness = 0.5.dp)

                            // USDT TRC20
                            Column {
                                Text(
                                    "Crypto / USDT (TRC20)",
                                    style      = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color      = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier          = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        USDT_ADDRESS,
                                        style    = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                        fontSize = 10.sp
                                    )
                                    IconButton(
                                        onClick  = { copyToClipboard(context, "USDT Address", USDT_ADDRESS) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "Copy USDT address",
                                            tint               = MaterialTheme.colorScheme.primary,
                                            modifier           = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    "⚠ TRC20 network only",
                                    style    = MaterialTheme.typography.labelSmall,
                                    color    = MaterialTheme.colorScheme.error,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // WhatsApp button
                    Button(
                        onClick  = {
                            val msg = "Hi, I want to buy LinkShield Pro. I have made the payment."
                            val url = "https://wa.me/$WHATSAPP_NUMBER?text=${URLEncoder.encode(msg, "UTF-8")}"
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                    ) {
                        Text("💬  Chat on WhatsApp", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "Enter your Pro License Key after payment:",
                        style     = MaterialTheme.typography.bodySmall,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value         = keyInput,
                        onValueChange = { keyInput = it.uppercase().take(20); keyError = null },
                        label         = { Text("Pro License Key") },
                        placeholder   = { Text("LSHD-XXXX-XXXX-CCCC") },
                        singleLine    = true,
                        isError       = keyError != null,
                        supportingText = {
                            keyError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType   = KeyboardType.Text,
                            capitalization = KeyboardCapitalization.Characters
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            when {
                                keyInput.isBlank() -> keyError = "Please enter your license key"
                                licenseManager.validateKey(keyInput.trim()) -> success = true
                                else -> keyError = "Invalid or already used on another device. Contact support."
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape    = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Lock, null, Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Activate Pro", style = MaterialTheme.typography.labelLarge)
                    }

                    TextButton(
                        onClick  = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Maybe Later", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentRow(label: String, value: String, subtitle: String, context: Context) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable { copyToClipboard(context, label, value) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.Default.ContentCopy,
            contentDescription = "Copy $label",
            tint     = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "$label copied!", Toast.LENGTH_SHORT).show()
}
