package com.reelguard.app

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
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

internal val Bg = Color(0xFFF4F6F8)
internal val Card = Color(0xFFFFFFFF)
internal val Line = Color(0xFFE3E8EE)
internal val TextHi = Color(0xFF14202B)
internal val TextLo = Color(0xFF64748B)
internal val OnAccent = Color(0xFF0B1F18)
internal val Light = FontFamily(Typeface.create("sans-serif-light", Typeface.NORMAL))
internal val Medium = FontFamily(Typeface.create("sans-serif-medium", Typeface.NORMAL))

class MainActivity : ComponentActivity() {
    private val serviceOn = mutableStateOf(false)
    private val tab = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReelStore.init(this)
        tab.intValue = intent.getIntExtra("tab", 0)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(background = Bg, surface = Bg, onSurface = TextHi)) {
                Surface(Modifier.fillMaxSize(), color = Bg) {
                    val consent by ReelStore.consent.collectAsState()
                    var skipped by remember { mutableStateOf(false) }
                    val open = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    when {
                        !consent -> Disclosure(onAgree = { ReelStore.setConsent(true) }, onDecline = { finishAffinity() })
                        !serviceOn.value && !skipped -> Setup(open, onSkip = { skipped = true })
                        else -> MainShell(serviceOn.value, tab.intValue, { tab.intValue = it }, open)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tab.intValue = intent.getIntExtra("tab", tab.intValue)
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
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).border(1.dp, Line, CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 22.sp, color = if (enabled) TextHi else TextLo.copy(alpha = 0.35f), fontFamily = Light) }
}

@Composable
fun Home(serviceOn: Boolean, openSettings: () -> Unit) {
    val count by ReelStore.count.collectAsState()
    val limit by ReelStore.limit.collectAsState()
    val ratio = count.toFloat() / limit
    val accent by animateColorAsState(Color(ReelStore.colorFor(ratio)), tween(500), label = "accent")
    val sweep by animateFloatAsState(ratio.coerceIn(0f, 1f), tween(600), label = "sweep")
    val glow by rememberInfiniteTransition(label = "glow").animateFloat(0.05f, 0.28f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "glowA")
    val last by ReelStore.lastEvent.collectAsState()
    val haptic = LocalHapticFeedback.current
    val feed by ReelStore.feed.collectAsState()
    val scanInfo by ReelStore.scanInfo.collectAsState()

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("REELGUARD", fontFamily = Medium, fontSize = 13.sp, letterSpacing = 3.sp, color = TextLo, modifier = Modifier.weight(1f))
            val dot = if (serviceOn) Color(0xFF10B981) else Color(0xFFEF4444)
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
                    .border(BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)), RoundedCornerShape(20.dp)).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Turn on tracking", fontFamily = Medium, fontSize = 16.sp, color = TextHi)
                Text("Enable ReelGuard under Installed apps in Accessibility settings so it can count your swipes.", fontSize = 14.sp, color = TextLo)
                Button(
                    onClick = openSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B), contentColor = OnAccent),
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
                if (ratio >= 0.8f && sweep > 0f) drawArc(accent.copy(alpha = glow), 135f, 270f * sweep, false, tl, sz, style = Stroke(stroke + 10.dp.toPx(), cap = StrokeCap.Round))
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

        val secs by ReelStore.scrollSecs.collectAsState()
        val breaksToday by ReelStore.breaks.collectAsState()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Time on reels", fmtTime(secs), "estimate", Modifier.weight(1.4f))
            StatTile("Breaks taken", "$breaksToday", "today", Modifier.weight(1f))
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Card).border(1.dp, Line, RoundedCornerShape(24.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Daily limit", fontFamily = Medium, fontSize = 15.sp, color = TextLo, modifier = Modifier.weight(1f))
                StepButton("\u2212", limit > ReelStore.MIN_LIMIT) { ReelStore.setLimit(limit - 5) }
                Text("$limit", fontFamily = Light, fontSize = 34.sp, color = TextHi, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 84.dp))
                StepButton("+", limit < ReelStore.MAX_LIMIT) { ReelStore.setLimit(limit + 5) }
            }
            Slider(
                value = limit.toFloat().coerceIn(5f, 300f),
                onValueChange = {
                    val n = (it / 5f).roundToInt() * 5
                    if (n != limit) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); ReelStore.setLimit(n) }
                },
                valueRange = ReelStore.MIN_LIMIT.toFloat()..ReelStore.MAX_LIMIT.toFloat(),
                steps = 58,
                colors = SliderDefaults.colors(
                    thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = Line,
                    activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent
                )
            )
            Row(Modifier.fillMaxWidth()) {
                Text("${ReelStore.MIN_LIMIT}", fontSize = 12.sp, color = TextLo, modifier = Modifier.weight(1f))
                Text("${ReelStore.MAX_LIMIT} max", fontSize = 12.sp, color = TextLo)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(20, 30, 50, 100).forEach { Pill("$it", limit == it, accent) { ReelStore.setLimit(it) } }
            }
        }

        var confirmReset by remember { mutableStateOf(false) }
        OutlinedButton(
            onClick = { confirmReset = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Line),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Card, contentColor = TextHi)
        ) {
            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Reset today\u2019s count", fontFamily = Medium, fontSize = 15.sp)
        }
        if (confirmReset) AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = Card,
            title = { Text("Reset today\u2019s count?", fontFamily = Medium, color = TextHi) },
            text = { Text("Reels, feed videos and time for today will go back to zero.", color = TextLo) },
            confirmButton = { TextButton(onClick = { ReelStore.reset(); confirmReset = false }) { Text("Reset", fontFamily = Medium, color = Color(0xFFEF4444)) } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel", fontFamily = Medium, color = TextLo) } }
        )
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card).border(1.dp, Line, RoundedCornerShape(20.dp)).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Feed videos", fontFamily = Medium, fontSize = 15.sp, color = TextHi)
                Text("Experimental \u00b7 videos that played while scrolling", fontSize = 12.sp, color = TextLo)
            }
            Text("$feed", fontFamily = Light, fontSize = 34.sp, color = TextHi)
        }
        Text("Video scan: $scanInfo", fontSize = 11.sp, color = TextLo.copy(alpha = 0.7f), textAlign = TextAlign.Center)
        Text("Last swipe seen: $last", fontSize = 11.sp, color = TextLo.copy(alpha = 0.7f), textAlign = TextAlign.Center)
    }
}


@Composable
private fun Screen(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        content = content
    )
}

@Composable
private fun Point(title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 7.dp).size(8.dp).clip(CircleShape).background(Color(0xFF10B981)))
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontFamily = Medium, fontSize = 16.sp, color = TextHi)
            Text(body, fontSize = 14.sp, color = TextLo, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(CircleShape).border(1.dp, Line, CircleShape), contentAlignment = Alignment.Center) {
            Text("$n", fontFamily = Medium, fontSize = 15.sp, color = TextHi)
        }
        Spacer(Modifier.width(14.dp))
        Text(text, fontSize = 15.sp, color = TextHi, lineHeight = 21.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
fun Disclosure(onAgree: () -> Unit, onDecline: () -> Unit) {
    Screen {
        Spacer(Modifier.height(16.dp))
        Text("REELGUARD", fontFamily = Medium, fontSize = 13.sp, letterSpacing = 3.sp, color = TextLo)
        Text("Your privacy comes first", fontFamily = Light, fontSize = 34.sp, color = TextHi, lineHeight = 40.sp)
        Text(
            "To count your reels, ReelGuard needs Android\u2019s Accessibility permission. Here is exactly how it is used.",
            fontSize = 15.sp, color = TextLo, lineHeight = 22.sp
        )
        Point("What it watches", "Only swipes inside Instagram, YouTube, Facebook and TikTok. Every other app is ignored.")
        Point("What it never reads", "Your messages, passwords, or payment details. It does not look at them, store them or share them.")
        Point("What it briefly sees", "Inside those apps it may glance at on-screen labels, such as a video caption, only to tell one video from the next. Nothing on screen is saved.")
        Point("Stays on your phone", "ReelGuard has no internet access and no account. Your counts never leave this device.")
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onAgree, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = OnAccent)
        ) { Text("Agree and continue", fontFamily = Medium, fontSize = 16.sp) }
        TextButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) { Text("Not now", fontFamily = Medium, color = TextLo) }
    }
}

@Composable
fun Setup(openSettings: () -> Unit, onSkip: () -> Unit) {
    val ctx = LocalContext.current
    var ready by remember { mutableStateOf(false) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        val t = TextToSpeech(ctx) { status -> ready = status == TextToSpeech.SUCCESS }
        tts = t
        onDispose { t.stop(); t.shutdown() }
    }
    val script = "To turn on ReelGuard, follow three steps. One: tap Open settings. " +
        "Two: find ReelGuard in the list of installed apps. " +
        "Three: switch it on, then tap Allow. After that, come back to ReelGuard."
    Screen {
        Spacer(Modifier.height(16.dp))
        Text("REELGUARD", fontFamily = Medium, fontSize = 13.sp, letterSpacing = 3.sp, color = TextLo)
        Text("Turn on tracking", fontFamily = Light, fontSize = 34.sp, color = TextHi)
        Text("Android requires you to switch this on yourself. It takes about 20 seconds.", fontSize = 15.sp, color = TextLo, lineHeight = 22.sp)
        Spacer(Modifier.height(4.dp))
        Step(1, "Tap Open settings below.")
        Step(2, "Find ReelGuard under Installed apps or Downloaded apps.")
        Step(3, "Switch it on, then tap Allow. Come back here afterwards.")
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = openSettings, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = OnAccent)
        ) { Text("Open settings", fontFamily = Medium, fontSize = 16.sp) }
        OutlinedButton(
            onClick = { tts?.speak(script, TextToSpeech.QUEUE_FLUSH, null, "guide") },
            enabled = ready, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Line)
        ) { Text("Play audio guide", fontFamily = Medium, fontSize = 16.sp, color = TextHi) }
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text("Skip for now", fontFamily = Medium, color = TextLo) }
    }
}


internal fun fmtTime(s: Int): String {
    val h = s / 3600
    val m = (s % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else if (m > 0) "${m}m" else "${s}s"
}

@Composable
private fun StatTile(label: String, value: String, note: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(Card).border(1.dp, Line, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, fontFamily = Medium, fontSize = 13.sp, color = TextLo)
        Text(value, fontFamily = Light, fontSize = 34.sp, color = TextHi)
        Text(note, fontSize = 11.sp, color = TextLo)
    }
}
