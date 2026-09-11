package com.linkshield.sandbox.ui.grabber

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * YouTube Login Consent Dialog
 * 
 * User ko batata hai ke YouTube download ke liye login chahiye,
 * aur uski cookies server par encrypted save hongi.
 * 
 * User "Agree" kare → Login WebView khule
 * User "Cancel" kare → Dialog band ho jaye
 */
@Composable
fun YouTubeLoginConsentDialog(
    onAgree: () -> Unit,
    onCancel: () -> Unit
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(12.dp))

                // Title
                Text(
                    text = "YouTube Login Required",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(16.dp))

                // Explanation
                Text(
                    text = "YouTube downloads ke liye aapka YouTube login chahiye. " +
                            "YouTube bot check ko bypass karne ke liye yeh zaroori hai.",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(16.dp))

                // What we save
                ConsentPoint(
                    icon = Icons.Default.Lock,
                    title = "Kya Save Hoga?",
                    description = "Aapki YouTube login cookies hamare server par " +
                            "encrypted save hongi — sirf aapke downloads ke liye."
                )

                Spacer(Modifier.height(12.dp))

                ConsentPoint(
                    icon = Icons.Default.Warning,
                    title = "Aapke Browsing Ka Kya?",
                    description = "Aapki baqi browsing history, cache, aur session " +
                            "app close hone par delete ho jayengi — jaise pehle hoti thi."
                )

                Spacer(Modifier.height(12.dp))

                ConsentPoint(
                    icon = Icons.Default.Lock,
                    title = "Aapka Control",
                    description = "Jab aap chahein, aap apni cookies delete kar sakte hain. " +
                            "Feature disable karne par sab cookies mit jayengi."
                )

                Spacer(Modifier.height(20.dp))

                // Buttons
                Button(
                    onClick = onAgree,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "I Agree — Continue to Login",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel", fontSize = 14.sp)
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Yeh feature optional hai. Agar aap YouTube download nahi " +
                            "karna chahte, toh aap cancel kar sakte hain.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun ConsentPoint(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(22.dp)
                .padding(top = 2.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}
