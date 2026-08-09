package com.linkshield.sandbox.ui.license

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.linkshield.sandbox.license.LicenseManager
import kotlinx.coroutines.launch

@Composable
fun ProUpgradeDialog(
    licenseManager: LicenseManager,
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var keyInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isValidating by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Upgrade to Pro", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "You've reached the 20 download limit. Upgrade to Pro for unlimited downloads and premium features.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Payment Methods", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        // Easypaisa Details
Text("Easypaisa: 03136176616", style = MaterialTheme.typography.bodySmall)
Spacer(modifier = Modifier.height(2.dp))

// JazzCash Details
Text("JazzCash: 03136176616", style = MaterialTheme.typography.bodySmall)
Spacer(modifier = Modifier.height(4.dp))

                        // 🔴 EDIT KARO: Apna USDT TRC20 address
                        Text("Crypto / USDT (TRC20): TQhUtaU9sg2hKfEM5FdeB3VGpzotKtwVub", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Send 500 PKR or 2 USDT and share screenshot on WhatsApp to get your 16-Digit Pro License Key.",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 🔴 EDIT KARO: Apna WhatsApp number (format: 923XXXXXXXXX)
                OutlinedButton(
                    onClick = {
                        val waNumber = "923136176616"
                        val message = "Hi, I want to buy LinkShield Pro License. I have made the payment."
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(
                                "https://wa.me/$waNumber?text=${java.net.URLEncoder.encode(message, "UTF-8")}"
                            )
                        )
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Contact on WhatsApp")
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it; error = null },
                    label = { Text("Enter 16-Digit Pro Key") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    isError = error != null,
                    supportingText = { error?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        scope.launch {
                            isValidating = true
                            error = null
                            val result = licenseManager.validateKeyOnline(keyInput)
                            isValidating = false
                            result.onSuccess { isValid ->
                                if (isValid) {
                                    onUnlocked()
                                } else {
                                    error = "Invalid license key"
                                }
                            }.onFailure {
                                error = "Error: ${it.message}"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = keyInput.length == 16 && !isValidating
                ) {
                    if (isValidating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verifying...")
                    } else {
                        Text("Activate Pro")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("Maybe Later")
                }
            }
        }
    }
}
