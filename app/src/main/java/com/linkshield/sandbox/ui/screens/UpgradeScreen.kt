package com.linkshield.sandbox.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun UpgradeScreen(
    trialDaysLeft: Int = 30,
    isTrialActive: Boolean = true
) {
    var licenseKey by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val usdtAddress = "TQhUtaU9sg2hKfEM5FdeB3VGpzotKtwVub"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Star, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("[ ⭐ PRO MEMBERSHIP ]", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (isTrialActive) "Status: Free Trial Active" else "Status: Trial Expired",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    text = "Remaining: $trialDaysLeft Days Left",
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Upgrade to Pro (Manual Payment):", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))

        PaymentCard(
            title = "🟢 EasyPaisa",
            accTitle = "Salman Latif",
            number = "03136176616",
            onCopy = { copyText(context, clipboardManager, "03136176616") }
        )

        Spacer(modifier = Modifier.height(8.dp))

        PaymentCard(
            title = "🔴 JazzCash",
            accTitle = "Salman Latif",
            number = "03061934345",
            onCopy = { copyText(context, clipboardManager, "03061934345") }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Fixed USDT Card Layout
        PaymentCard(
            title = "🟢 USDT (TRC20)",
            accTitle = "Network: TRC20 (Tron)",
            number = "TQhUtaU9sg2hKfEM5FdeB3VGpzotKtwVub",
            onCopy = { copyText(context, clipboardManager, usdtAddress) }
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text("Activate Pro License:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = licenseKey,
            onValueChange = { licenseKey = it },
            placeholder = { Text("[ 🔑 XXXX-XXXX-XXXX-XXXX ]") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("[ 🚀 Activate License ]", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PaymentCard(
    title: String,
    accTitle: String,
    number: String,
    onCopy: () -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Text Details wrapped properly to avoid Copy button overflow
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(accTitle, fontSize = 12.sp)
                Text(
                    text = "Number: $number",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))

            OutlinedButton(
                onClick = onCopy,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy", fontSize = 11.sp)
            }
        }
    }
}

private fun copyText(context: Context, clipboard: androidx.compose.ui.platform.ClipboardManager, text: String) {
    clipboard.setText(AnnotatedString(text))
    Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
}
