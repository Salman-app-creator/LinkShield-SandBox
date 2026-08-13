package com.linkshield.sandbox.ui.upgrade

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashOn
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.license.LicenseManager
import java.net.URLEncoder

// ── Payment constants ───────────────────────────────────────────────────────
private const val WHATSAPP_NUMBER  = "923136176616"
private const val EASYPAISA_NUMBER = "03136176616"
private const val EASYPAISA_TITLE  = "Salman Latif"
private const val JAZZCASH_NUMBER  = "03061934345"
private const val JAZZCASH_TITLE   = "Salman Latif"
private const val USDT_ADDRESS     = "TQhUtaU9sg2hKfEM5FdeB3VGpzotKtwVub"
private const val PRICE_PKR        = "350"
private const val PRICE_USD        = "1.25"

@Composable
fun UpgradeScreen(
    licenseManager: LicenseManager,
    dnsManager:     DnsManager,
    isDark:         Boolean,
    onToggleTheme:  () -> Unit,
    onUnlocked:     () -> Unit
) {
    val context = LocalContext.current

    val isPro       = licenseManager.isProUser()
    val trialActive = licenseManager.trialActive()
    val trialDays   = licenseManager.trialDaysRemaining()
    val remaining   = licenseManager.getRemainingDownloads()

    var keyInput   by remember { mutableStateOf("") }
    var keyError   by remember { mutableStateOf<String?>(null) }
    var keySuccess by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ═══════════════════════════════════════════════════════════════════
        // HEADER
        // ═══════════════════════════════════════════════════════════════════
        Card(
            shape  = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            )
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector        = Icons.Default.FlashOn,
                    contentDescription = null,
                    modifier           = Modifier.size(48.dp),
                    tint               = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Upgrade to LinkShield Pro",
                    style      = MaterialTheme.typography.headlineSmall,
                    color      = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Center
                )
                Text(
                    "One-time payment · Unlimited downloads · Full DNS Shield · All future updates",
                    style     = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ═══════════════════════════════════════════════════════════════════
        // SECTION 1 — TRIAL STATUS BANNER
        // ═══════════════════════════════════════════════════════════════════
        Card(
            shape  = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isPro       -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    trialActive -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                    else        -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.20f)
                }
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (isPro) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "PRO USER",
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Unlimited downloads & Shield access", style = MaterialTheme.typography.bodyMedium)
                } else if (trialActive) {
                    Text(
                        "Free Trial Active",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        "$trialDays days remaining — Shield is free during trial",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        "Trial Expired",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "Upgrade to Pro to continue using DNS Shield & downloads.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Remaining free downloads: ${if (remaining == Int.MAX_VALUE) "∞" else remaining.toString()}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        HorizontalDivider()

        // ═══════════════════════════════════════════════════════════════════
        // SECTION 2 — PAYMENT GATEWAYS
        // ═══════════════════════════════════════════════════════════════════
        Text(
            "Payment Methods",
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Card(
            shape  = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Price pill
                Surface(
                    shape    = RoundedCornerShape(10.dp),
                    color    = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Rs. $PRICE_PKR  /  $$PRICE_USD  (one-time)",
                        style      = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary,
                        textAlign  = TextAlign.Center,
                        modifier   = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
                    )
                }

                // EasyPaisa
                PaymentRow(
                    label    = "EasyPaisa",
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

                // Binance / USDT TRC20
                Column {
                    Text(
                        "Binance Wallet / USDT (TRC20)",
                        style      = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        "⚠ TRC20 network only",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // WhatsApp button
        Button(
            onClick = {
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

        HorizontalDivider()

        // ═══════════════════════════════════════════════════════════════════
        // SECTION 3 — LICENSE KEY ACTIVATION
        // ═══════════════════════════════════════════════════════════════════
        Text(
            "Activate License Key",
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        AnimatedVisibility(visible = keySuccess) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF00E676))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Pro activated successfully!",
                    color = Color(0xFF00E676),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (!keySuccess) {
            OutlinedTextField(
                value         = keyInput,
                onValueChange = { keyInput = it.uppercase().take(20); keyError = null },
                label         = { Text("License Key (LSHD-XXXX-XXXX-CCCC)") },
                singleLine    = true,
                isError       = keyError != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType   = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.Characters
                ),
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(10.dp)
            )
            keyError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = {
                    when {
                        keyInput.isBlank() -> keyError = "Enter your license key"
                        licenseManager.validateKey(keyInput.trim()) -> {
                            keySuccess = true
                            onUnlocked()
                        }
                        else -> keyError = "Invalid or already used key. Contact support on WhatsApp."
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Lock, null, Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Activate Pro")
            }
        }

        HorizontalDivider()

        // Theme toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Dark Theme", style = MaterialTheme.typography.titleMedium)
            Switch(checked = isDark, onCheckedChange = { onToggleTheme() })
        }

        HorizontalDivider()

        // About
        Text("About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("LinkShield Sandbox v2.1", style = MaterialTheme.typography.bodyMedium)
        Text("Privacy Sandbox + DNS Shield + Media Grabber", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(32.dp))
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
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(
            onClick = { copyToClipboard(context, label, value) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = "Copy $label",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "$label copied!", Toast.LENGTH_SHORT).show()
}
