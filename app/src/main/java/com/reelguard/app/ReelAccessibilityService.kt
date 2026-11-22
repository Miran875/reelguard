package com.reelguard.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.widget.LinearLayout
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine

class ReelAccessibilityService : AccessibilityService() {

    // package -> view-id keywords that prove the user is in the reels/shorts player.
    // Empty list = every vertical scroll counts (TikTok is all short video).
    private val apps = mapOf(
        "com.instagram.android" to listOf("clips", "reel"),
        "com.google.android.youtube" to listOf("shorts", "reel_"),
        "com.facebook.katana" to listOf("reel"),
        "com.zhiliaoapp.musically" to emptyList<String>(),
        "com.ss.android.ugc.trill" to emptyList<String>()
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var wm: WindowManager
    private var root: FrameLayout? = null
    private lateinit var fill: View
    private lateinit var label: TextView
    private var lastCount = 0L
    private var lastReelTime = 0L
    private var breakView: View? = null
    private val ideas = listOf(
        "\uD83D\uDCA7 Drink a glass of water", "\uD83D\uDEB6 Walk for five minutes", "\uD83E\uDD38 Stretch your neck and shoulders",
        "\uD83D\uDCD6 Read two pages of a book", "\uD83C\uDF24 Look out of a window for 20 seconds"
    )
    private var lastIdx = -1
    private var lastIdxTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val scan = Runnable { scanFeed() }
    private var lastSig: String? = null
    private var lastEventTime = 0L
    private var acc = 0
    private var counted = false
    private var shown = false

    override fun onServiceConnected() {
        ReelStore.init(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        buildOverlay()
        scope.launch {
            ReelStore.count.combine(ReelStore.limit) { c, l -> c to l }.collect { (c, l) -> update(c, l) }
        }
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString() ?: return
        if (pkg == "com.android.systemui" || pkg == packageName) return
        val keywords = apps[pkg]
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ->
                if (keywords != null) showOverlay() else hideOverlay()
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (keywords == null) return
                val now = System.currentTimeMillis()
                val h = resources.displayMetrics.heightPixels
                val dy = if (Build.VERSION.SDK_INT >= 28) Math.abs(e.scrollDeltaY) else 0
                val src = e.source
                val chain = idChain(src)
                ReelStore.lastEvent.value = "$pkg dy=$dy from=${e.fromIndex} to=${e.toIndex} cls=${src?.className?.toString()?.substringAfterLast('.')} ids=$chain"
                if (now - lastEventTime > 250) { acc = 0; counted = false }   // new swipe starts after a pause
                lastEventTime = now
                // Only the scrolled view (or its parents) may be the reels/shorts player.
                if (keywords.isNotEmpty() && keywords.none { k -> chain.contains(k, ignoreCase = true) }) {
                    handler.removeCallbacks(scan); handler.postDelayed(scan, 450)   // feed scroll: look for a playing video
                    return
                }
                // Preferred: the app reports page position, so count every forward page advance (handles fast swipes).
                val idx = e.fromIndex
                if (idx >= 0 && e.toIndex >= 0) {
                    if (now - lastIdxTime > 30000) lastIdx = -1
                    lastIdxTime = now
                    if (lastIdx >= 0 && idx > lastIdx) {
                        repeat(Math.min(idx - lastIdx, 5)) {
                            onReel()
                        }
                    }
                    lastIdx = idx
                    return
                }
                // Fallback: no page position reported, count once per swipe burst.
                if (counted) return
                acc += dy
                if (dy == 0 || acc >= h * 0.25f) {      // dy==0: app does not report distance
                    counted = true
                    onReel()
                }
            }
        }
    }

    private fun inReels(keys: List<String>): Boolean {
        val r = rootInActiveWindow ?: return false
        var seen = 0
        fun walk(n: AccessibilityNodeInfo?, depth: Int): Boolean {
            if (n == null || depth > 12 || seen++ > 400) return false
            val id = n.viewIdResourceName
            if (id != null && keys.any { id.contains(it, ignoreCase = true) }) return true
            for (i in 0 until n.childCount) if (walk(n.getChild(i), depth + 1)) return true
            return false
        }
        return walk(r, 0)
    }

    private fun firstText(n: AccessibilityNodeInfo?, budget: IntArray): String {
        if (n == null || budget[0]-- <= 0) return ""
        val t = n.contentDescription?.toString() ?: n.text?.toString()
        if (!t.isNullOrBlank() && t.length > 3) return t.take(40)
        for (i in 0 until n.childCount) { val r = firstText(n.getChild(i), budget); if (r.isNotEmpty()) return r }
        return ""
    }

    /** Experimental: finds the video player that fills most of the screen and counts it once per new video. */
    private fun scanFeed() {
        val root = rootInActiveWindow ?: return
        val h = resources.displayMetrics.heightPixels
        val r = Rect()
        var best: AccessibilityNodeInfo? = null
        var bestVis = 0
        var found = 0
        var seen = 0
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 14 || seen++ > 700) return
            val cls = n.className?.toString() ?: ""
            val id = n.viewIdResourceName ?: ""
            val video = cls.contains("TextureView") || cls.contains("SurfaceView") || cls.contains("VideoView") ||
                cls.contains("PlayerView") || id.contains("video", true) || id.contains("player", true)
            if (video && n.isVisibleToUser) {
                found++
                n.getBoundsInScreen(r)
                val vis = Math.min(r.bottom, h) - Math.max(r.top, 0)
                if (vis > bestVis) { bestVis = vis; best = n }
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        val b = best
        if (b == null || bestVis < h * 0.35f) {
            lastSig = null
            ReelStore.scanInfo.value = "video nodes=$found, none dominant"
            return
        }
        val parentText = firstText(b.parent ?: b, intArrayOf(60))
        val sig = (b.className?.toString()?.substringAfterLast('.') ?: "") + "|" + (b.viewIdResourceName?.substringAfter(":id/") ?: "") + "|" + parentText
        ReelStore.scanInfo.value = "video nodes=$found vis=${bestVis * 100 / h}% sig=$sig"
        if (sig != lastSig) { lastSig = sig; ReelStore.refresh(); ReelStore.incrementFeed() }
    }

    private fun onReel() {
        val now = System.currentTimeMillis()
        ReelStore.refresh()
        val c = ReelStore.increment()
        warn(c, ReelStore.limit.value)
        val gap = now - lastReelTime
        lastReelTime = now
        val sec = if (gap in 1L..60000L) (gap / 1000).toInt().coerceAtLeast(1) else 25
        if (ReelStore.addScrollSeconds(sec)) showBreakCard()
    }

    private fun hideBreakCard() {
        breakView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        breakView = null
    }

    private fun showBreakCard() {
        if (breakView != null) return
        val d = resources.displayMetrics.density
        val mins = ReelStore.scrollSecs.value / 60
        fun tv(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
            text = t; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
        }
        fun btn(t: String, bg: Int, fg: Int, click: () -> Unit) = TextView(this).apply {
            text = t; textSize = 15f; setTextColor(fg); gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
            setPadding((16 * d).toInt(), (13 * d).toInt(), (16 * d).toInt(), (13 * d).toInt())
            background = GradientDrawable().apply { cornerRadius = 14 * d; setColor(bg) }
            setOnClickListener { click() }
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((22 * d).toInt(), (22 * d).toInt(), (22 * d).toInt(), (18 * d).toInt())
            background = GradientDrawable().apply { cornerRadius = 24 * d; setColor(0xFF121826.toInt()); setStroke((1 * d).toInt(), 0xFF232C3F.toInt()) }
        }
        card.addView(tv("Time for a breather", 20f, 0xFFF1F4FA.toInt(), true))
        card.addView(tv("You\u2019ve scrolled for about $mins minutes today (${ReelStore.count.value} reels).", 14f, 0xFF8B96AB.toInt())
            .apply { setPadding(0, (8 * d).toInt(), 0, (14 * d).toInt()) })
        card.addView(tv("Try instead:  ${ideas.random()}", 16f, 0xFF34D399.toInt()).apply { setPadding(0, 0, 0, (18 * d).toInt()) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(btn("Take a break", 0xFF34D399.toInt(), 0xFF0A0E16.toInt()) {
            hideBreakCard()
            try {
                startActivity(Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("tab", 1))
            } catch (_: Exception) {}
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = (10 * d).toInt() })
        row.addView(btn("Keep scrolling", 0xFF232C3F.toInt(), 0xFFF1F4FA.toInt()) { hideBreakCard() }, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(row)
        val lp = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.9f).toInt(), -2, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        try {
            wm.addView(card, lp); breakView = card
            val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
            v.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }

    private fun idChain(n: AccessibilityNodeInfo?): String {
        val out = ArrayList<String>()
        var cur = n
        var i = 0
        while (cur != null && i < 6) {
            cur.viewIdResourceName?.let { out.add(it.substringAfter(":id/")) }
            cur = cur.parent
            i++
        }
        return out.joinToString(">")
    }

    private fun warn(c: Int, l: Int) {
        val hit = c == (l * 0.8f).toInt() || c == l || (c > l && (c - l) % 5 == 0)
        if (!hit) return
        val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
        val pattern = if (c >= l) longArrayOf(0, 250, 100, 250, 100, 400) else longArrayOf(0, 150, 80, 150)
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pattern, -1)) else v.vibrate(pattern, -1)
    }

    private fun buildOverlay() {
        val d = resources.displayMetrics.density
        val track = View(this).apply { setBackgroundColor(0x66000000) }
        fill = View(this)
        label = TextView(this).apply {
            textSize = 11f; setTextColor(Color.WHITE); setPadding((8 * d).toInt(), (2 * d).toInt(), (8 * d).toInt(), (2 * d).toInt())
            background = GradientDrawable().apply { cornerRadius = 12 * d; setColor(0xB3000000.toInt()) }
        }
        root = FrameLayout(this).apply {
            addView(track, FrameLayout.LayoutParams(-1, (6 * d).toInt()))
            addView(fill, FrameLayout.LayoutParams(0, (6 * d).toInt()))
            addView(label, FrameLayout.LayoutParams(-2, -2, Gravity.END).apply { topMargin = (10 * d).toInt(); marginEnd = (8 * d).toInt() })
        }
    }

    private fun update(c: Int, l: Int) {
        val w = resources.displayMetrics.widthPixels
        val ratio = c.toFloat() / l
        fill.layoutParams = (fill.layoutParams as FrameLayout.LayoutParams).also { it.width = (w * ratio.coerceAtMost(1f)).toInt() }
        fill.setBackgroundColor(ReelStore.colorFor(ratio))
        label.text = if (c > l) "$c reels  (+${c - l} over)" else "$c / $l reels"
        label.background = (label.background as GradientDrawable).apply { setColor(if (ratio >= 1f) 0xE6C62828.toInt() else 0xB3000000.toInt()) }
        root?.requestLayout()
    }

    private fun showOverlay() {
        if (shown) return
        val lp = WindowManager.LayoutParams(
            -1, -2, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP }
        try { wm.addView(root, lp); shown = true } catch (_: Exception) {}
    }

    private fun hideOverlay() {
        if (!shown) return
        try { wm.removeView(root) } catch (_: Exception) {}
        shown = false
    }

    override fun onInterrupt() {}
    override fun onDestroy() { handler.removeCallbacks(scan); hideBreakCard(); hideOverlay(); scope.cancel(); super.onDestroy() }
}
