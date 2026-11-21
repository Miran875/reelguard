package com.reelguard.app

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val Bg = Color(0xFF0A0E16)
private val Card = Color(0xFF121826)
private val Line = Color(0xFF232C3F)
private val TextHi = Color(0xFFF1F4FA)
private val TextLo = Color(0xFF8B96AB)
private val Light = FontFamily(Typeface.create("sans-serif-light", Typeface.NORMAL))
private val Medium = FontFamily(Typeface.create("sans-serif-medium", Typeface.NORMAL))

class MainActivity : ComponentActivity() {
    private val serviceOn = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReelStore.init(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Bg, onSurface = TextHi)) {
                Surface(Modifier.fillMaxSize(), color = Bg) {
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
private fun Pill(text: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.clip(shape)
            .background(if (selected) accent.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, if (selected) accent else Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) { Text(text, fontFamily = Medium, fontSize = 13.sp, color = if (selected) accent else TextLo) }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).border(1.dp, Line, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 22.sp, color = TextHi, fontFamily = Light) }
}

@Composable
fun Home(serviceOn: Boolean, openSettings: () -> Unit) {
    val count by ReelStore.count.collectAsState()
    val limit by ReelStore.limit.collectAsState()
    val ratio = count.toFloat() / limit
    val accent by animateColorAsState(Color(ReelStore.colorFor(ratio)), tween(500), label = "accent")
    val sweep by animateFloatAsState(ratio.coerceIn(0f, 1f), tween(600), label = "sweep")
    val last by ReelStore.lastEvent.collectAsState()

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("REELGUARD", fontFamily = Medium, fontSize = 13.sp, letterSpacing = 3.sp, color = TextLo, modifier = Modifier.weight(1f))
            val dot = if (serviceOn) Color(0xFF34D399) else Color(0xFFEF4444)
            Row(
                Modifier.clip(RoundedCornerShape(50)).border(1.dp, Line, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(8.dp))
                Text(if (serviceOn) "Tracking" else "Paused", fontFamily = Medium, fontSize = 12.sp, color = TextHi)
            }
        }

        if (!serviceOn) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card)
                    .border(BorderStroke(1.dp, Color(0xFFFBBF24).copy(alpha = 0.5f)), RoundedCornerShape(20.dp)).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Turn on tracking", fontFamily = Medium, fontSize = 16.sp, color = TextHi)
                Text("Enable ReelGuard under Installed apps in Accessibility settings so it can count your swipes.", fontSize = 14.sp, color = TextLo)
                Button(
                    onClick = openSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBBF24), contentColor = Bg),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Open settings", fontFamily = Medium) }
            }
        }

        Box(Modifier.size(270.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 20.dp.toPx()
                val tl = Offset(stroke / 2, stroke / 2)
                val sz = Size(size.width - stroke, size.height - stroke)
                drawArc(Line, 135f, 270f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
                if (sweep > 0f) drawArc(accent, 135f, 270f * sweep, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$count", fontFamily = Light, fontSize = 92.sp, color = if (ratio >= 1f) accent else TextHi)
                Text("of $limit reels", fontFamily = Medium, fontSize = 14.sp, color = TextLo)
            }
        }

        Text(
            when {
                count == 0 -> "Fresh start. Nothing watched yet."
                ratio < 0.5f -> "You\u2019re doing fine."
                ratio < 0.8f -> "Over halfway. Keep an eye on it."
                ratio < 1f -> "Almost at your limit. Time to wrap up."
                ratio == 1f -> "Limit reached. Time to put the phone down."
                else -> "${count - limit} over your limit. Close the app."
            },
            fontFamily = Medium, fontSize = 16.sp, color = TextHi, textAlign = TextAlign.Center
        )

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Card).border(1.dp, Line, RoundedCornerShape(24.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Daily limit", fontFamily = Medium, fontSize = 15.sp, color = TextLo, modifier = Modifier.weight(1f))
                StepButton("\u2212") { ReelStore.setLimit(limit - 5) }
                Text("$limit", fontFamily = Light, fontSize = 34.sp, color = TextHi, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 84.dp))
                StepButton("+") { ReelStore.setLimit(limit + 5) }
            }
            Slider(
                value = limit.toFloat().coerceIn(5f, 300f),
                onValueChange = { ReelStore.setLimit((it / 5f).roundToInt() * 5) },
                valueRange = 5f..300f,
                steps = 58,
                colors = SliderDefaults.colors(
                    thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = Line,
                    activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent
                )
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(20, 30, 50, 100).forEach { Pill("$it", limit == it, accent) { ReelStore.setLimit(it) } }
            }
        }

        TextButton(onClick = { ReelStore.reset() }) {
            Text("Reset today\u2019s count", fontFamily = Medium, color = TextLo)
        }
        Text("Last swipe seen: $last", fontSize = 11.sp, color = TextLo.copy(alpha = 0.7f), textAlign = TextAlign.Center)
    }
}
