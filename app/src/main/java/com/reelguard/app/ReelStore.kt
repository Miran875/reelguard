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

    fun init(ctx: Context) {
        if (::p.isInitialized) return
        p = ctx.applicationContext.getSharedPreferences("reels", Context.MODE_PRIVATE)
        limit.value = p.getInt("limit", 30)
        refresh()
    }

    private fun key() = "c_" + LocalDate.now()
    fun refresh() { count.value = p.getInt(key(), 0) }

    fun increment(): Int {
        val v = p.getInt(key(), 0) + 1
        p.edit().putInt(key(), v).apply()
        count.value = v
        return v
    }

    fun reset() { p.edit().putInt(key(), 0).apply(); count.value = 0 }

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
