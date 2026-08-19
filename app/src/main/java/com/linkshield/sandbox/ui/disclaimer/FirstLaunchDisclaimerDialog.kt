package com.linkshield.sandbox.ui.disclaimer

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.linkshield.sandbox.R

@Composable
fun FirstLaunchDisclaimerDialog(onAccept: () -> Unit) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.94f),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(
                Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_app_logo),
                    contentDescription = "LinkShield Sandbox",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(92.dp).clip(CircleShape)
                )
                Spacer(Modifier.height(12.dp))
                Text("Welcome to LinkShield Sandbox", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("Privacy-first sandbox browser with a dedicated media Grabber.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                Text(
                    "Browse, check and prepare media downloads in one place. Backend security and download engines remain isolated and will be integrated in later phases.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(18.dp))
                Button(onClick = onAccept, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                    Text("Accept & Continue", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
