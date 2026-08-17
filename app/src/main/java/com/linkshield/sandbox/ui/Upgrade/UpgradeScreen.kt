package com.linkshield.sandbox.ui.Upgrade

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
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshield.sandbox.license.LicenseManager
import java.net.URLEncoder

private const val WHATSAPP_NUMBER = "923136176616"

private const val EASYPAISA_NUMBER = "03136176616"
private const val EASYPAISA_TITLE = "Salman Latif"

private const val JAZZCASH_NUMBER = "03061934345"
private const val JAZZCASH_TITLE = "Salman Latif"

private const val USDT_ADDRESS = "TQhUtaU9sg2hKfEM5FdeB3VGpzotKtwVub"

private const val PRICE_PKR = "350"
private const val PRICE_USD = "1.25"

@Composable
fun UpgradeScreen(
    licenseManager: LicenseManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var keyInput by remember { mutableStateOf("") }
    var keyError by remember { mutableStateOf<String?>(null) }
    var isProUnlocked by remember { mutableStateOf(licenseManager.isProUser()) }

    val screenshotPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            sharePaymentScreenshotToWhatsApp(context, uri)
        }
    }

    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Upgrade to LinkShield Pro",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            "One-time payment. Lifetime access. All features unlocked.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)
        ) {
            Text(
                "Rs. $PRICE_PKR / $$PRICE_USD (one-time)",
                modifier = Modifier.padding(12.dp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Pro Features Included",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                ProFeatureRow("Unlimited DoH Protection", "Encrypted DNS for browsing")
                ProFeatureRow("Media Grabber", "High-quality media extraction")
                ProFeatureRow("Unlimited Downloads", "No 20-download cap")
                ProFeatureRow("Lifetime License", "One-time activation")
            }
        }

        if (isProUnlocked) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Check, null, Modifier.size(48.dp))
                    Text(
                        "Pro License Active!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Payment Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(12.dp))

                    PaymentRow(
                        label = "EasyPaisa",
                        accountNumber = EASYPAISA_NUMBER,
                        accountTitle = EASYPAISA_TITLE,
                        context = context
                    )

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    PaymentRow(
                        label = "JazzCash",
                        accountNumber = JAZZCASH_NUMBER,
                        accountTitle = JAZZCASH_TITLE,
                        context = context
                    )

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    Text(
                        "USDT (TRC20)",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, "Copy USDT address")
                        }
                    }

                    Text(
                        "TRC20 network only",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Button(
                onClick = {
                    val message = """
                        Hi, I want to buy LinkShield Pro.
                        Payment amount: Rs. $PRICE_PKR
                        Please verify my payment and send my Pro license key.
                    """.trimIndent()

                    openWhatsApp(
                        context = context,
                        message = message
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF25D366)
                )
            ) {
                Text(
                    "WhatsApp Support",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = {
                    screenshotPicker.launch("image/*")
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Attach Payment Screenshot in WhatsApp")
            }

            Text(
                "You can either open WhatsApp with payment details, or choose your " +
                    "payment screenshot and share it directly to WhatsApp.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Activate License Key",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = {
                            keyInput = it.uppercase().take(24)
                            keyError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Enter License Key") },
                        isError = keyError != null,
                        supportingText = {
                            keyError?.let {
                                Text(it, color = MaterialTheme.colorScheme.error)
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(Modifier.height(10.dp))

                    Button(
                        onClick = {
                            val key = keyInput.trim()
                            if (key.isBlank()) {
                                keyError = "Please enter your license key."
                            } else {
                                scope.launch {
                                    keyError = null
                                    val valid = licenseManager.validateKey(key)
                                    if (valid) {
                                        isProUnlocked = true
                                        Toast.makeText(
                                            context,
                                            "Pro Activated!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        keyError = "Invalid key, GitHub validation failed, or key is already used on this device."
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Lock, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Activate Pro Key")
                    }
                }
            }
        }

        Spacer(Modifier.height(70.dp))
    }
}

@Composable
private fun PaymentRow(
    label: String,
    accountNumber: String,
    accountTitle: String,
    context: Context
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                copyToClipboard(
                    context,
                    label,
                    "$accountNumber\n$accountTitle"
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Account Number: $accountNumber",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                "Account Title: $accountTitle",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Icon(
            Icons.Default.ContentCopy,
            contentDescription = "Copy $label",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

private fun openWhatsApp(context: Context, message: String) {
    val encoded = URLEncoder.encode(message, "UTF-8")
    val whatsappUri = Uri.parse("https://wa.me/$WHATSAPP_NUMBER?text=$encoded")

    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, whatsappUri)
        )
    }.onFailure {
        Toast.makeText(
            context,
            "WhatsApp open nahi ho saka. Please install/update WhatsApp.",
            Toast.LENGTH_LONG
        ).show()
    }
}


private fun sharePaymentScreenshotToWhatsApp(
    context: Context,
    imageUri: Uri
) {
    val message = "Hi, I have paid Rs. $PRICE_PKR for LinkShield Pro. Please verify the attached payment screenshot and send my Pro license key."

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/*"
        putExtra(Intent.EXTRA_STREAM, imageUri)
        putExtra(Intent.EXTRA_TEXT, message)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        setPackage("com.whatsapp")
    }

    runCatching {
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(
            context,
            "WhatsApp installed nahi hai ya share failed. Screenshot manually WhatsApp par send karein.",
            Toast.LENGTH_LONG
        ).show()
    }
}

private fun copyToClipboard(
    context: Context,
    label: String,
    text: String
) {
    val clipboard =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "$label copied!", Toast.LENGTH_SHORT).show()
}

@Composable
private fun ProFeatureRow(
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Default.Check,
            null,
            Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
