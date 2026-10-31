package com.ngocdevv.nativecook.blocks.modals.coindropreward

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.floor

internal object CoinDropRewardTheme {
    val page = Color(0xFFFBFAFF)
    val ink = Color(0xFF191A23)
    val mute = Color(0xFF8B8D9A)
    val line = Color(0xFFEEEDF5)
    val accent = Color(0xFF6A5CFF)
    val blue = Color(0xFF2F6BFF)
    val blueSoft = Color(0xFFEAF0FF)
    val tile = Color.White
    val stamp = Color(0xFFF1F0F7)
    val missions = listOf("Cedar desk lamp", "Linen throw blanket")
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val stamped = setOf(0, 1, 2, 4)
    const val ROW_H = 72f
    const val COIN_D = 44f
    const val COIN_S = 22f
    const val HERO_S = 66f
    const val TILE = 236f
    fun fonts(context: Context) = FontFamily(
        Font("coindropreward/coindropreward_Inter-Regular.ttf", context.assets, FontWeight.Normal),
        Font("coindropreward/coindropreward_Inter-Medium.ttf", context.assets, FontWeight.Medium),
        Font("coindropreward/coindropreward_Inter-SemiBold.ttf", context.assets, FontWeight.SemiBold),
    )
    data class Layout(val w: Float, val h: Float, val top: Float, val bottom: Float) {
        val side = 20f
        val walletTop = top + 8
        val titleTop = walletTop + 40 + 12
        val sectionTop = titleTop + 30 + 4 + 20 + 28
        val rowsTop = sectionTop + 18 + 8
        val rowCoin = (0..1).map { Offset(side + COIN_D / 2, rowsTop + it * ROW_H + ROW_H / 2) }
        val bonusLabelTop = rowsTop + 2 * ROW_H + 20
        val bonusTop = bonusLabelTop + 18 + 8
        val tileCx = w / 2
        val walletLeft = w - side - 124
        val walletIcon = Offset(walletLeft + 24, walletTop + 20)
        val daysTop = h - maxOf(bottom, 16f) - 76
        val tileCy = floor((bonusTop + ROW_H + daysTop) / 2 + 0.5f)
        val toastCoin = Offset(tileCx, tileCy - 24)
        val vector = walletIcon - toastCoin
        val span = vector.getDistance()
        val direction = vector / span
    }
}
