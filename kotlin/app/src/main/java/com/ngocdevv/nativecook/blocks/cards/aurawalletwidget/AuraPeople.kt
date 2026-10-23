package com.ngocdevv.nativecook.blocks.cards.aurawalletwidget

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

// The six original portraits and the invented transaction from the source.
internal object AuraPeople {
    val ids = arrayOf("kofi", "maya", "theo", "raj", "skye", "ana")
    val names = arrayOf("Kofi", "Maya", "Theo", "Raj", "Skye", "Ana")
    val grounds = intArrayOf(0xFFA9E6BE.toInt(), 0xFFCDBDF6.toInt(), 0xFFF5B8CB.toInt(),
        0xFFF1DBC6.toInt(), 0xFFA9D8F7.toInt(), 0xFFBFE3F2.toInt())
    const val owner = 5
    const val title = "Orbit Pro"
    const val date = "Jan 17"
    const val time = "20:12"
    private val whole = NumberFormat.getIntegerInstance(Locale.US)

    fun formatCents(cents: Long): String {
        val absolute = abs(cents)
        val fraction = (absolute % 100).toString().padStart(2, '0')
        return "${if (cents < 0) "−" else ""}${whole.format(absolute / 100)}.$fraction"
    }
}
