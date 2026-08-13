package com.linkshield.sandbox.ui.upgrade

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshield.sandbox.license.LicenseManager
import java.net.URLEncoder

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
    licenseManager: LicenseManager
) {
    val context = LocalContext.current
    var keyInput by remember { mutableStateOf("") }
    var keyError by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(licenseManager.isProUnlocked()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header Banner
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Upgrade to LinkShield Pro",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Remove download limits and unlock premium security features.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Features List
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Why Upgrade to Pro?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                ProFeatureRow("Unlimited Media Downloads", "No daily or total limit on Green Hole Grabber.")
                Spacer(modifier = Modifier.height(8.dp))
                ProFeatureRow("Fast Track Processing", "High-speed media extraction and 4K video support.")
                Spacer(modifier = Modifier.height(8.dp))
                ProFeatureRow("Advanced DNS Guard & Ad-Blocker", "Strict tracking protection and custom blocklists.")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Payment Details Section
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Payment Methods",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Rs. $PRICE_PKR  /  \$$PRICE_USD  (One-time Lifetime)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
                    )
                }

                PaymentRow("Easypaisa", EASYPAISA_NUMBER, "Account Title: $EASYPAISA_TITLE", context)
                HorizontalDivider(thickness = 0.5.dp)
                PaymentRow("JazzCash", JAZZCASH_NUMBER, "Account Title: $JAZZCASH_TITLE", context)
                HorizontalDivider(thickness = 0.5.dp)

                Column {
                    Text("Crypto / USDT (TRC20)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(USDT_ADDRESS, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), fontSize = 10.sp)
                        IconButton(
                            onClick = { copyToClipboard(context, "USDT Address", USDT_ADDRESS) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, "Copy", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                    Text("⚠ TRC20 network only", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // WhatsApp Direct Link
        Button(
            onClick = {
                val msg = "Hi, I would like to purchase a LinkShield Pro key. I have completed the payment."
                val url = "https://wa.me/$WHATSAPP_NUMBER?text=${URLEncoder.encode(msg, "UTF-8")}"
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
        ) {
            Text("💬  Send Payment Proof on WhatsApp", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Key Activation Form
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Activate License Key",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (isSuccess) {
                    Text(
                        text = "Status: PRO UNLOCKED 🎉",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it.uppercase().take(20); keyError = null },
                        label = { Text("Enter Pro License Key") },
                        placeholder = { Text("LSHD-XXXX-XXXX-CCCC") },
                        singleLine = true,
                        isError = keyError != null,
                        supportingText = {
                            keyError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            capitalization = KeyboardCapitalization.Characters
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            when {
                                keyInput.isBlank() -> keyError = "Please enter your license key"
                                licenseManager.validateKey(keyInput.trim()) -> isSuccess = true
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
        
        Spacer(modifier = Modifier.height(80.dp)) // Padding for bottom navbar
    }
}

@Composable
private fun ProFeatureRow(title: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(text = desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
