package com.linkshield.sandbox.ui.screens

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun UpgradeScreen(trialDaysLeft: Int = 30, isTrialActive: Boolean = true) {
    var licenseKey by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 1. Status Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Status: ${if (isTrialActive) "Free Trial Active" else "Trial Ended"}",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Remaining: $trialDaysLeft Days Left",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }

        // 2. PRICING CARD
        ProPricingCard()

        Spacer(modifier = Modifier.height(20.dp))

        // 3. MANUAL PAYMENT SECTION
        Text(
            text = "Upgrade to Pro (Manual Payment):",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        PaymentMethodCard(
            name = "EasyPaisa",
            holder = "Salman Latif",
            number = "03136176616",
            color = Color(0xFF4CAF50)
        )

        PaymentMethodCard(
            name = "JazzCash",
            holder = "Salman Latif",
            number = "03061934345",
            color = Color(0xFFF44336)
        )

        PaymentMethodCard(
            name = "USDT (TRC20)",
            holder = "Network: TRC20 (Tron)",
            number = "TQhUtaU9sg2hKfEM5FdeB3VG...",
            color = Color(0xFF4CAF50)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // WHATSAPP SUPPORT BUTTON (SINGLE)
        Button(
            onClick = {
                val phone = "+923136176616"
                val message = "Hi, I have sent the payment. Please verify and send my Pro License Key."
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://api.whatsapp.com/send?phone=$phone&text=${Uri.encode(message)}")
                }
                context.startActivity(intent)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
        ) {
            Text(
                text = "💬 Send Payment Proof on WhatsApp",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 4. LICENSE ACTIVATION INPUT
        Text(
            text = "Activate Pro License:",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = licenseKey,
            onValueChange = { licenseKey = it },
            placeholder = { Text("🔑 XXXX-XXXX-XXXX-XXXX") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { /* Handle License Activation */ },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
        ) {
            Text(
                text = "🚀 Activate License",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun ProPricingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2C))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF00E5FF).copy(alpha = 0.15f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LIFETIME PRO LICENSE",
                        color = Color(0xFF00E5FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = "Upgrade to LinkShield Pro",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 10.dp)
            )

            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                Text(
                    text = "Rs 350",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF00E5FF)
                )
                Text(
                    text = " PKR / 1.25 USDT",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Divider(color = Color.Gray.copy(alpha = 0.2f))

            Spacer(modifier = Modifier.height(8.dp))

            FeatureItem("Unlimited Media Downloads", "No daily limits on Grabber engine")
            FeatureItem("4K Video & High-Speed Extraction", "Fast track server processing")
            FeatureItem("Advanced DNS Guard & Ad-Blocker", "Strict tracking protection & custom rules")
            FeatureItem("Restricted Website Unblocker", "Built-in sandboxed proxy tunnel")
        }
    }
}

@Composable
fun FeatureItem(title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF00E5FF),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(text = subtitle, fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
fun PaymentMethodCard(name: String, holder: String, number: String, color: Color) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, fontWeight = FontWeight.Bold, color = color)
                Text(text = holder, fontSize = 12.sp)
                Text(text = number, fontWeight = FontWeight.Medium)
            }

            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Payment Details", number)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "$name number copied!", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy Number",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
