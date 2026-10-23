package com.ngocdevv.nativecook.blocks.gestures.gumballcrank

import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

internal object GumballCrankColor {
    val WallTop = Color.rgb(0xF1, 0xE9, 0xDC)
    val WallBottom = Color.rgb(0xE5, 0xD8, 0xC4)
    val TableTop = Color.rgb(0xC7, 0x9B, 0x6B)
    val TableEdge = Color.rgb(0xE2, 0xBE, 0x92)
    val TableFront = Color.rgb(0xA8, 0x7B, 0x4D)
    val TableDeep = Color.rgb(0x8A, 0x60, 0x38)
    val Ink = Color.rgb(0x2A, 0x21, 0x1B)
    val Mute = Color.rgb(0x7A, 0x6B, 0x5E)
    val Enamel = Color.rgb(0xB4, 0x23, 0x2C)
    val EnamelLight = Color.rgb(0xE0, 0x48, 0x4E)
    val EnamelDeep = Color.rgb(0x6E, 0x10, 0x16)
    val ChromeHi = Color.rgb(0xFB, 0xFB, 0xF9)
    val ChromeMid = Color.rgb(0xB9, 0xBC, 0xC1)
    val ChromeLow = Color.rgb(0x6B, 0x6F, 0x76)
    val ChromeDeep = Color.rgb(0x3E, 0x42, 0x48)
    val Slot = Color.rgb(0x1C, 0x15, 0x14)
    val Card = Color.rgb(0xFF, 0xFC, 0xF6)
    val Coin = Color.rgb(0xE9, 0xB6, 0x40)
    val CoinLight = Color.rgb(0xFF, 0xE0, 0x8A)
    val CoinDeep = Color.rgb(0xA8, 0x76, 0x1C)
    val Mint = Color.rgb(0x4F, 0xBF, 0xA5)
    val MintDeep = Color.rgb(0x2B, 0x8C, 0x75)
    val MintLight = Color.rgb(0x9B, 0xE3, 0xCF)
    val Balls = intArrayOf(0xFFE8433F.toInt(), 0xFFF6C744.toInt(), 0xFF7CC46A.toInt(), 0xFFF08A3C.toInt(), 0xFFF4EFE6.toInt(), 0xFF4C8FD6.toInt(), 0xFFEE8DB5.toInt(), 0xFF8B5FBF.toInt(), 0xFF47B9A8.toInt())
}

internal object GumballCrankCopy {
    const val Title = "Daily treat"
    const val Subtitle = "Day 7 of your streak"
    const val Hint = "Turn the handle once around"
    const val Busy = "Here it comes"
    const val Done = "Come back tomorrow for day 8"
    const val Eyebrow = "DAY 7 REWARD"
    const val Prize = "+50"
    const val Unit = "coins"
    const val CardSubtitle = "Spend them on themes and streak shields."
    const val Collect = "Collect"
}

internal object GumballCrankMetrics {
    const val StageW = 402f
    const val StageH = 778f
    const val GlobeX = 201f
    const val GlobeY = 254f
    const val GlobeR = 122f
    const val GlobeInner = 117f
    const val CollarY = 370f
    const val PlateY = 462f
    const val DoorTop = 532f
    const val TableY = 614f
    const val TrayY = 690f
    const val HintBase = 752f
    const val CardX = 42f
    const val CardY = 420f
    const val CardW = 318f
    const val CardH = 236f
    const val ButtonX = 66f
    const val ButtonY = 580f
    const val ButtonW = 270f
    const val ButtonH = 52f
}

internal data class GumballCrankLayout(val width: Float, val height: Float, val scale: Float, val ox: Float, val oy: Float) {
    companion object {
        fun create(width: Float, height: Float, top: Float, bottom: Float): GumballCrankLayout {
            val room = height - top - max(bottom, 16f)
            val scale = min(width / GumballCrankMetrics.StageW, room / GumballCrankMetrics.StageH)
            return GumballCrankLayout(width, height, scale, (width - GumballCrankMetrics.StageW * scale) / 2f, top + (room - GumballCrankMetrics.StageH * scale) / 2f)
        }
    }
}
