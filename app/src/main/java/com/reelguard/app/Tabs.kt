package com.reelguard.app

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Green = Color(0xFF34D399)

@Composable
fun MainShell(serviceOn: Boolean, tab: Int, onTab: (Int) -> Unit, openSettings: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            Crossfade(tab, label = "tab") { t ->
                when (t) {
                    0 -> Home(serviceOn, openSettings)
                    1 -> RelaxScreen()
                    else -> LearnScreen()
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(Card).navigationBarsPadding().padding(vertical = 6.dp)) {
            NavItem(Icons.Rounded.Home, "Today", tab == 0, Modifier.weight(1f)) { onTab(0) }
            NavItem(Icons.Rounded.Favorite, "Relax", tab == 1, Modifier.weight(1f)) { onTab(1) }
            NavItem(Icons.Rounded.Info, "Learn", tab == 2, Modifier.weight(1f)) { onTab(2) }
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = if (selected) Green else TextLo
    Column(modifier.clickable(onClick = onClick).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = c)
        Text(label, fontFamily = Medium, fontSize = 11.sp, color = c)
    }
}

@Composable
private fun TabScreen(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        content = content
    )
}

@Composable
private fun InfoCard(title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card).border(1.dp, Line, RoundedCornerShape(20.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, fontFamily = Medium, fontSize = 16.sp, color = TextHi)
        Text(body, fontSize = 14.sp, color = TextLo, lineHeight = 21.sp)
    }
}

private data class Idea(val emoji: String, val title: String, val body: String)

private val ideas = listOf(
    Idea("\uD83D\uDCA7", "Drink a glass of water", "Walk to the kitchen and refill your bottle."),
    Idea("\uD83D\uDEB6", "Walk for five minutes", "Around the block, or just around the room."),
    Idea("\uD83E\uDD38", "Stretch for a minute", "Roll your shoulders, reach up, loosen your neck."),
    Idea("\uD83D\uDCD6", "Read two pages", "Keep a book where your phone usually sits."),
    Idea("\uD83C\uDF24", "Look outside", "Find something far away and watch it for 20 seconds."),
    Idea("\u270D\uFE0F", "Write one line", "What do you want to get done today?")
)

@Composable
fun RelaxScreen() {
    val breaks by ReelStore.breaks.collectAsState()
    var running by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf("Tap start") }
    var cycles by remember { mutableIntStateOf(0) }
    val scale = remember { Animatable(0.55f) }
    val done = remember { mutableStateListOf<Int>() }

    LaunchedEffect(running) {
        if (running) {
            while (true) {
                phase = "Breathe in"
                scale.animateTo(1f, tween(4000, easing = FastOutSlowInEasing))
                phase = "Hold"
                delay(1500)
                phase = "Breathe out"
                scale.animateTo(0.55f, tween(6000, easing = FastOutSlowInEasing))
                cycles++
                if (cycles == 3) ReelStore.addBreak()
            }
        } else {
            phase = "Tap start"
            scale.animateTo(0.55f, tween(600))
        }
    }

    TabScreen {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("RELAX", fontFamily = Medium, fontSize = 13.sp, letterSpacing = 3.sp, color = TextLo, modifier = Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(50)).border(1.dp, Line, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Favorite, null, tint = Green, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("$breaks breaks today", fontFamily = Medium, fontSize = 12.sp, color = TextHi)
            }
        }
        Text("Take three slow breaths", fontFamily = Light, fontSize = 30.sp, color = TextHi, lineHeight = 36.sp)

        Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(270.dp)) {
                val r = size.minDimension / 2f
                val s = scale.value
                drawCircle(color = Green.copy(alpha = 0.08f), radius = r * s, center = center)
                drawCircle(color = Green.copy(alpha = 0.16f), radius = r * s * 0.82f, center = center)
                drawCircle(
                    brush = Brush.radialGradient(listOf(Color(0xFF6EE7B7), Color(0xFF0EA5A4)), center = center, radius = r * s * 0.62f),
                    radius = r * s * 0.62f, center = center
                )
            }
            Text(phase, fontFamily = Medium, fontSize = 18.sp, color = Color(0xFF06231B), textAlign = TextAlign.Center)
        }
        Button(
            onClick = { if (running) running = false else { cycles = 0; running = true } },
            modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (running) Line else Green, contentColor = if (running) TextHi else Bg)
        ) { Text(if (running) "Stop" else "Start breathing", fontFamily = Medium, fontSize = 16.sp) }
        if (running) Text("Breaths: $cycles", fontSize = 13.sp, color = TextLo, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(4.dp))
        Text("Try this instead of scrolling", fontFamily = Medium, fontSize = 16.sp, color = TextHi)
        ideas.forEachIndexed { i, idea ->
            val isDone = i in done
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card)
                    .border(1.dp, if (isDone) Green else Line, RoundedCornerShape(20.dp))
                    .clickable { if (!isDone) { done.add(i); ReelStore.addBreak() } }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Green.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Text(idea.emoji, fontSize = 22.sp)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(idea.title, fontFamily = Medium, fontSize = 15.sp, color = TextHi)
                    Text(idea.body, fontSize = 13.sp, color = TextLo, lineHeight = 18.sp)
                }
                if (isDone) Icon(Icons.Rounded.Check, null, tint = Green)
            }
        }
        Text("Tap an idea when you\u2019ve done it to log a break.", fontSize = 12.sp, color = TextLo, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun LearnScreen() {
    val count by ReelStore.count.collectAsState()
    val mins = count * 25 / 60f
    val monthHours = count * 30 * 25 / 3600f
    TabScreen {
        Text("LEARN", fontFamily = Medium, fontSize = 13.sp, letterSpacing = 3.sp, color = TextLo)
        Text("Why it matters", fontFamily = Light, fontSize = 30.sp, color = TextHi)

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Card).border(1.dp, Line, RoundedCornerShape(24.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Your numbers", fontFamily = Medium, fontSize = 15.sp, color = TextLo)
            Text("%.0f min".format(mins), fontFamily = Light, fontSize = 46.sp, color = TextHi)
            Text("spent on $count reels today", fontSize = 14.sp, color = TextLo)
            Text(
                "At today\u2019s pace that is about %.1f hours a month.".format(monthHours),
                fontFamily = Medium, fontSize = 15.sp, color = Green, modifier = Modifier.padding(top = 8.dp)
            )
            Text("Estimate based on about 25 seconds per reel. Yours may differ.", fontSize = 12.sp, color = TextLo)
        }

        Text("What research says", fontFamily = Medium, fontSize = 16.sp, color = TextHi)
        InfoCard(
            "Attention and self-control",
            "A 2025 review in Psychological Bulletin pooled 71 studies and about 98,000 people. Heavier short-video use went along with poorer attention and weaker impulse control."
        )
        InfoCard(
            "Stress and mood",
            "The same review linked heavy use with more stress and anxiety. A 2026 review in the Journal of Medical Internet Research also found links with depression and anxiety."
        )
        InfoCard(
            "Sleep",
            "The link with poorer sleep was weak but consistent, and it showed up most for late-night scrolling. A good reason to set a stricter limit at night."
        )
        InfoCard(
            "What this does not prove",
            "These studies find associations, not proof of cause. People who feel stressed or low may also scroll more. The goal is not guilt, it is noticing when scrolling is no longer a choice."
        )
        Text(
            "Sources: Nguyen et al., Psychological Bulletin, 2025. Tang et al., Journal of Medical Internet Research, 2026.",
            fontSize = 11.sp, color = TextLo, lineHeight = 16.sp
        )
    }
}
