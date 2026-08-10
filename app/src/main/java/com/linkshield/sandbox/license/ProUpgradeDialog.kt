package com.linkshield.sandbox.ui.license

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.linkshield.sandbox.license.LicenseManager

// ─────────────────────────────────────────────────────────────────────────────
// EDIT YOUR DETAILS HERE — only change these constants, nothing else needed
// ─────────────────────────────────────────────────────────────────────────────
private const val WHATSAPP_NUMBER   = "923136176616"          // 92XXXXXXXXXX format, no +
private const val EASYPAISA_NUMBER  = "03136176616"
private const val EASYPAISA_TITLE   = "Your Name"            // Apna Easypaisa account title
private const val JAZZCASH_NUMBER   = "03061934345"
private const val JAZZCASH_TITLE    = "Your Name"            // Apna JazzCash account title
private const val USDT_ADDRESS      = "TQhUtaU9sg2hKfEM5FdeB3VGpzotKtwVub"  // TRC20 wallet
private const val PRICE_PKR         = "500"
private const val PRICE_USDT        = "2"
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ProUpgradeDialog(
    licenseManager: LicenseManager,
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    var keyInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (success) {
                    // ── SUCCESS STATE ──────────────────────────────────────────
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Pro Unlocked! 🎉",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "You now have unlimited downloads and all premium features.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onUnlocked,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Continue", style = MaterialTheme.typography.labelLarge)
                    }

                } else {
                    // ── UPGRADE STATE ─────────────────────────────────────────
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Upgrade to Pro",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Get unlimited downloads, Shield protection & all future features.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // ── PAYMENT CARD ───────────────────────────────────────────
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {

                            // Price badge
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Price: ${PRICE_PKR} PKR  /  ${PRICE_USDT} USDT",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // ── Easypaisa ─────────────────────────────────────
                            PaymentRow(
                                label = "Easypaisa",
                                value = EASYPAISA_NUMBER,
                                subtitle = "Account: $EASYPAISA_TITLE",
                                context = context
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                            // ── JazzCash ──────────────────────────────────────
                            PaymentRow(
                                label = "JazzCash",
                                value = JAZZCASH_NUMBER,
                                subtitle = "Account: $JAZZCASH_TITLE",
                                context = context
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                            // ── USDT TRC20 ────────────────────────────────────
                            Text(
                                "Crypto / USDT (TRC20)",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = USDT_ADDRESS,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = {
                                        copyToClipboard(context, "USDT Address", USDT_ADDRESS)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy USDT Address",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Text(
                                "⚠ Only send USDT on TRC20 network",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ── WhatsApp button ────────────────────────────────────────
                    OutlinedButton(
                        onClick = {
                            val message = "Hi, I want to buy LinkShield Pro License. I have made the payment."
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(
                                    "https://wa.me/$WHATSAPP_NUMBER?text=${java.net.URLEncoder.encode(message, "UTF-8")}"
                                )
                            )
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("💬 Chat on WhatsApp", style = MaterialTheme.typography.labelLarge)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // ── License key input ──────────────────────────────────────
                    Text(
                        "Enter your Pro Key below after payment:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = {
                            keyInput = it.uppercase()
                            error = null
                        },
                        label = { Text("Pro License Key") },
                        placeholder = { Text("LSHD-XXXX-XXXX-CCCC") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text
                        ),
                        singleLine = true,
                        isError = error != null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (error != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (keyInput.isBlank()) {
                                error = "Please enter a license key"
                                return@Button
                            }
                            val isValid = licenseManager.validateKey(keyInput.trim())
                            if (isValid) {
                                success = true
                            } else {
                                error = "Invalid or already used key. Contact support on WhatsApp."
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Activate Pro", style = MaterialTheme.typography.labelLarge)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("Maybe Later", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/**
 * Reusable payment row with a single-tap copy button.
 */
@Composable
private fun PaymentRow(
    label: String,
    value: String,
    subtitle: String,
    context: Context
) {
    Column {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { copyToClipboard(context, label, value) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy $label",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied!", Toast.LENGTH_SHORT).show()
}
