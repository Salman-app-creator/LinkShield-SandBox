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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.linkshield.sandbox.license.LicenseManager

@Composable
fun ProUpgradeDialog(
    licenseManager: LicenseManager,
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    var keyInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

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
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("WhatsApp Support", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("+92 3XX-XXXXXXX", style = MaterialTheme.typography.bodyLarge)
                        Text("Send payment screenshot here to get your Pro Key", style = MaterialTheme.typography.labelSmall)

                        Spacer(modifier = Modifier.height(12.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Easypaisa / JazzCash", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("03XX-XXXXXXX", style = MaterialTheme.typography.bodyLarge)
                        Text("Account Title: Your Name", style = MaterialTheme.typography.labelSmall)

                        Spacer(modifier = Modifier.height(12.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Crypto / USDT (TRC20)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("TX...your...wallet...address...here", style = MaterialTheme.typography.bodySmall)
                        Text("Only send USDT on TRC20 network", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Price: 500 PKR  or  2 USDT", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = {
                        val waNumber = "923XXXXXXXXX"
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
                    Text("Chat on WhatsApp")
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
                        if (licenseManager.validateKey(keyInput)) {
                            onUnlocked()
                        } else {
                            error = "Invalid license key"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = keyInput.length == 16 || keyInput.replace("-", "").length == 16
                )
                
