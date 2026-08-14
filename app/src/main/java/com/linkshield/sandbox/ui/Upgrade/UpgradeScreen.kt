package com.linkshield.sandbox.ui.Upgrade

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun UpgradeScreen() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Upgrade to Pro License",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Unlock high-speed DoH servers, unlimited media downloads, and advanced privacy shield features.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(20.dp))

        // EasyPaisa Card
        PaymentCard(
            title = "EasyPaisa",
            accountTitle = "Salman Latif",
            accountNumber = "03136176616",
            onCopy = { copyToClipboard(context, "03136176616", "EasyPaisa Number") }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // JazzCash Card
        PaymentCard(
            title = "JazzCash",
            accountTitle = "Salman Latif",
            accountNumber = "03061934345",
            onCopy = { copyToClipboard(context, "03061934345", "JazzCash Number") }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // USDT TRC20 Card
        PaymentCard(
            title = "USDT (TRC20 Network)",
            accountTitle = "Wallet Address",
            accountNumber = "TYu23x89A1zLp90KqM3vR7u8W",
            onCopy = { copyToClipboard(context, "TYu23x89A1zLp90KqM3vR7u8W", "USDT Address") }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // WhatsApp Launcher Button (Standard Send Icon Fix)
        Button(
            onClick = {
                val whatsappUrl = "https://wa.me/923136176616?text=Hello%2C%20I%20have%20completed%20the%20payment%20for%20LinkShield%20Pro.%20Please%20verify%20my%20receipt%20and%20provide%20the%20license%20key."
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(whatsappUrl))
                context.startActivity(intent)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "WhatsApp",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Contact Support on WhatsApp",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PaymentCard(
    title: String,
    accountTitle: String,
    accountNumber: String,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = accountTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = accountNumber,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy Details"
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String, label: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied to clipboard!", Toast.LENGTH_SHORT).show()
}
