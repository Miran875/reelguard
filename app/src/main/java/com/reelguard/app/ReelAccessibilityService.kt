package com.reelguard.app

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
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
                if (now - lastEventTime > 500) { acc = 0; counted = false }   // new swipe starts after a pause
                lastEventTime = now
                // Only the scrolled view (or its parents) may be the reels/shorts player.
                if (keywords.isNotEmpty() && keywords.none { k -> chain.contains(k, ignoreCase = true) }) return
                if (counted) return
                acc += dy
                if (dy == 0 || acc >= h * 0.25f) {      // dy==0: app does not report distance
                    counted = true
                    ReelStore.refresh()
                    warn(ReelStore.increment(), ReelStore.limit.value)
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
    override fun onDestroy() { hideOverlay(); scope.cancel(); super.onDestroy() }
}
