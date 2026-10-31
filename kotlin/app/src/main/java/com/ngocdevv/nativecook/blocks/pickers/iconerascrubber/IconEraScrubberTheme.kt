package com.ngocdevv.nativecook.blocks.pickers.iconerascrubber

import androidx.compose.ui.graphics.Color

/** Type, copy and layout constants ported from ies-theme.ts. */
object IconEraScrubberTheme {
    val Ink = Color(0xFFF3F4F8)
    val Mute = Color(0xFF8C8FA0)
    val Faint = Color(0xFF3A3C4A)
    val Tick = Color(0xFF5A5D6E)
    val Accent = Color(0xFFFFFFFF)
    val Background = Color(0xFF09090F)

    const val RegularFont = "iconerascrubber/iconerascrubber_Inter-Regular.ttf"
    const val MediumFont = "iconerascrubber/iconerascrubber_Inter-Medium.ttf"
    const val SemiBoldFont = "iconerascrubber/iconerascrubber_Inter-SemiBold.ttf"

    data class Era(val name: String, val line: String)

    val Eras = listOf(
        Era("Glossy", "Gloss, bevels and airmail paper"),
        Era("Flat", "Thin lines and clean colour"),
        Era("Soft", "Rounded light and gentle depth"),
        Era("Glass", "Clear glass that bends the light"),
    )

    const val AppName = "Postbox"
    const val AppSub = "App icon history"
    const val Step = 28f
    const val IconHalf = 110f

    data class Layout(
        val width: Float,
        val height: Float,
        val titleTop: Float,
        val iconX: Float,
        val iconY: Float,
        val yearTop: Float,
        val rulerTop: Float,
    )

    fun makeLayout(width: Float, height: Float, top: Float, bottom: Float): Layout {
        val titleTop = top + 12f
        val iconY = kotlin.math.round(top + (height - top - bottom) * 0.36f)
        val yearTop = iconY + IconHalf + 44f
        val rulerTop = height - maxOf(bottom, 16f) - 96f
        return Layout(width, height, titleTop, width / 2f, iconY, yearTop, rulerTop)
    }
}
