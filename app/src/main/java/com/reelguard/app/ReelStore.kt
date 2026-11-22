package com.reelguard.app

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

/** Stores per-day reel counts on the device and exposes live state. */
object ReelStore {
    private lateinit var p: SharedPreferences
    val count = MutableStateFlow(0)
    val limit = MutableStateFlow(30)
    val lastEvent = MutableStateFlow("no swipe seen yet")
    val feed = MutableStateFlow(0)
    val consent = MutableStateFlow(false)
    val breaks = MutableStateFlow(0)
    val scrollSecs = MutableStateFlow(0)
    val scanInfo = MutableStateFlow("no video scan yet")

    fun init(ctx: Context) {
        if (::p.isInitialized) return
        p = ctx.applicationContext.getSharedPreferences("reels", Context.MODE_PRIVATE)
        limit.value = p.getInt("limit", 30)
        consent.value = p.getBoolean("consent", false)
        refresh()
    }

    private fun key() = "c_" + LocalDate.now()
    private fun fkey() = "f_" + LocalDate.now()
    private fun bkey() = "b_" + LocalDate.now()
    private fun skey() = "s_" + LocalDate.now()
    fun refresh() {
        count.value = p.getInt(key(), 0); feed.value = p.getInt(fkey(), 0)
        breaks.value = p.getInt(bkey(), 0); scrollSecs.value = p.getInt(skey(), 0)
    }
    fun addBreak() { val v = p.getInt(bkey(), 0) + 1; p.edit().putInt(bkey(), v).apply(); breaks.value = v }
    /** Adds estimated scrolling time. Returns true when this crosses a new full hour. */
    fun addScrollSeconds(sec: Int): Boolean {
        val before = p.getInt(skey(), 0); val after = before + sec
        p.edit().putInt(skey(), after).apply(); scrollSecs.value = after
        return after / 3600 > before / 3600
    }
    fun incrementFeed() { val v = p.getInt(fkey(), 0) + 1; p.edit().putInt(fkey(), v).apply(); feed.value = v }

    fun increment(): Int {
        val v = p.getInt(key(), 0) + 1
        p.edit().putInt(key(), v).apply()
        count.value = v
        return v
    }

    fun setConsent(v: Boolean) { p.edit().putBoolean("consent", v).apply(); consent.value = v }

    fun reset() { p.edit().putInt(key(), 0).putInt(fkey(), 0).putInt(skey(), 0).apply(); count.value = 0; feed.value = 0; scrollSecs.value = 0 }

    fun setLimit(v: Int) {
        val n = v.coerceIn(5, 500)
        p.edit().putInt("limit", n).apply()
        limit.value = n
    }

    /** Emerald -> amber -> crimson as ratio goes 0 -> 0.6 -> 1. */
    fun colorFor(ratio: Float): Int {
        val r = ratio.coerceIn(0f, 1f)
        val green = 0xFF34D399.toInt(); val amber = 0xFFFBBF24.toInt(); val red = 0xFFEF4444.toInt()
        return if (r < 0.6f) androidx.core.graphics.ColorUtils.blendARGB(green, amber, r / 0.6f)
        else androidx.core.graphics.ColorUtils.blendARGB(amber, red, (r - 0.6f) / 0.4f)
    }
}
