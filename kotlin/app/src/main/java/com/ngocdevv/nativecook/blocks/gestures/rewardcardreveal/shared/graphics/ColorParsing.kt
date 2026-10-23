package com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.shared.graphics

import androidx.compose.ui.graphics.Color

/**
 * Parses the CSS colour literals used by the React Native sources so they can be copied verbatim:
 * `#rgb`, `#rrggbb`, `#rrggbbaa`, `rgb(r, g, b)` and `rgba(r, g, b, a)`.
 */
fun cssColor(value: String): Color {
    val text = value.trim()
    if (text.startsWith("#")) {
        val hex = text.substring(1)
        return when (hex.length) {
            3 -> {
                val r = hex[0].digitToInt(16) * 17
                val g = hex[1].digitToInt(16) * 17
                val b = hex[2].digitToInt(16) * 17
                Color(r, g, b)
            }
            6 -> Color(0xFF000000L or hex.toLong(16))
            8 -> {
                val rgb = hex.substring(0, 6).toLong(16)
                val alpha = hex.substring(6, 8).toLong(16)
                Color((alpha shl 24) or rgb)
            }
            else -> error("Unsupported hex colour: $value")
        }
    }

    val open = text.indexOf('(')
    val close = text.lastIndexOf(')')
    require(open > 0 && close > open) { "Unsupported colour literal: $value" }
    val parts = text.substring(open + 1, close).split(',').map { it.trim() }
    val r = parts[0].toFloat() / 255f
    val g = parts[1].toFloat() / 255f
    val b = parts[2].toFloat() / 255f
    val a = if (parts.size > 3) parts[3].toFloat() else 1f
    return Color(r, g, b, a)
}

fun cssColors(vararg values: String): List<Color> = values.map(::cssColor)
