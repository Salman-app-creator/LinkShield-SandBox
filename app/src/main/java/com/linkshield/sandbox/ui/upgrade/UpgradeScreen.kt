package com.linkshield.sandbox.ui.upgrade

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshield.sandbox.R
import com.linkshield.sandbox.license.LicenseManager

@Composable
fun UpgradeScreen(
    licenseManager: LicenseManager? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var key by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Stylish Top Header with App Logo
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher),
                    contentDescription = "App Logo",
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "LinkShield Pro",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                "Unlimited Grabber downloads and premium access",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }

        ProBenefitsCard()

        PaymentInformationCard(context)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Activate Pro",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "UI-only activation form. License validation will be connected in the backend phase.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.uppercase().take(32); message = null },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Pro License Key") },
                    shape = RoundedCornerShape(12.dp)
                )
                Button(
                    onClick = {
                        message = if (key.isBlank()) {
                            "Enter your license key."
                        } else {
                            "Activation will be enabled with the license engine in the next phase."
                        }
                    },
                    enabled = key.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Activate Pro")
                }
                message?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(22.dp))
    }
}

@Composable
private fun ProBenefitsCard() {
    val gradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF102B35),
            Color(0xFF073E49),
            Color(0xFF18213A)
        )
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .background(gradient, RoundedCornerShape(22.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.12f)
                ) {
                    // Star Icon Reverted Back
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.padding(9.dp).size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "PRO BENEFITS",
                        color = Color(0xFF7FF7FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "Unlock the full LinkShield experience",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            PremiumBenefit("Unlimited media download access")
            PremiumBenefit("Higher-quality download choices")
            PremiumBenefit("Priority access to future engine integrations")
            PremiumBenefit("No free-download counter")
        }
    }
}


@Composable
private fun PremiumBenefit(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = Color(0xFF00D6A3).copy(alpha = 0.18f)
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color(0xFF63FFD0),
                modifier = Modifier.padding(5.dp).size(17.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PaymentInformationCard(context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Payment Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            PaymentRow(
                label = "EasyPaisa",
                value = "03136176616",
                subtitle = "Salman Latif",
                brand = PaymentBrand.EASYPAISA,
                context = context
            )
            HorizontalDivider()
            PaymentRow(
                label = "JazzCash",
                value = "03061934345",
                subtitle = "Salman Latif",
                brand = PaymentBrand.JAZZCASH,
                context = context
            )
            HorizontalDivider()
            PaymentRow(
                label = "USDT (TRC20)",
                value = "TQhUtaU9sg2hKfEM5FdeB3VGpzotKtwVub",
                subtitle = "TRC20 only",
                brand = PaymentBrand.USDT,
                context = context
            )
            Text(
                "Price: Rs. 350 / $1.25",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private enum class PaymentBrand { EASYPAISA, JAZZCASH, USDT }

@Composable
@Composable
private fun PaymentBrandIcon(brand: PaymentBrand) {
    val drawableRes = when (brand) {
        PaymentBrand.EASYPAISA -> R.drawable.ic_easypaisa
        PaymentBrand.JAZZCASH -> R.drawable.ic_jazzcash
        PaymentBrand.USDT -> R.drawable.ic_usdt
    }

    // Uniform size with clean transparent background for transparent PNGs
    Box(
        modifier = Modifier.size(42.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = drawableRes),
            contentDescription = brand.name,
            modifier = Modifier.fillMaxSize()
        )
    }
}


@Composable
private fun PaymentRow(
    label: String,
    value: String,
    subtitle: String,
    brand: PaymentBrand,
    context: Context
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PaymentBrandIcon(brand)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Bold)
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
            Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
        }) {
            Icon(Icons.Default.ContentCopy, "Copy $label")
        }
    }
}
