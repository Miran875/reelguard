package com.reelguard.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private val serviceOn = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReelStore.init(this)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Home(serviceOn.value) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ReelStore.refresh()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        serviceOn.value = enabled.contains("$packageName/${ReelAccessibilityService::class.java.name}")
    }
}

@Composable
fun Home(serviceOn: Boolean, openSettings: () -> Unit) {
    val count by ReelStore.count.collectAsState()
    val limit by ReelStore.limit.collectAsState()
    val ratio = count.toFloat() / limit
    val accent = Color(ReelStore.colorFor(ratio))

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("ReelGuard", fontSize = 22.sp, fontWeight = FontWeight.Bold)

        if (!serviceOn) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Counting is off", fontWeight = FontWeight.Bold)
                    Text("Turn on ReelGuard under Installed apps in Accessibility settings so it can count your swipes.")
                    Button(onClick = openSettings) { Text("Open settings") }
                }
            }
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Text("$count", fontSize = 96.sp, fontWeight = FontWeight.ExtraBold, color = accent)
            Text("  of $limit reels today", fontSize = 18.sp, modifier = Modifier.padding(bottom = 18.dp))
        }

        LinearProgressIndicator(
            progress = { ratio.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(20.dp),
            color = accent,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )

        Text(
            when {
                count == 0 -> "Fresh start. Nothing watched yet."
                ratio < 0.5f -> "You're doing fine."
                ratio < 0.8f -> "Over halfway. Keep an eye on it."
                ratio < 1f -> "Almost at your limit. Time to wrap up."
                else -> "Limit reached. Put the phone down."
            },
            fontSize = 16.sp
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Daily limit", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { ReelStore.setLimit(limit - 5) }) { Text("−5") }
            Text("$limit", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = { ReelStore.setLimit(limit + 5) }) { Text("+5") }
        }

        OutlinedButton(onClick = { ReelStore.reset() }, modifier = Modifier.fillMaxWidth()) { Text("Reset today") }
        val last by ReelStore.lastEvent.collectAsState()
        Text("Last swipe seen: $last", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
