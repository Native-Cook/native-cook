package com.ngocdevv.nativecook.blocks.gestures.raffledrumspin

import androidx.compose.ui.graphics.Color

internal const val RDS_REF_W = 402f
internal const val RDS_REF_H = 874f

internal object RdsCopy {
    const val title = "Raffle Rumble"
    const val sub1 = "Buy tickets to enter the raffle"
    const val sub2 = "for awesome prizes."
    val chips = arrayOf("15+", "$1+", "unlimited")
    const val favourite = "Favorite Games"
    val cardLines = arrayOf(
        arrayOf("A single chance to", "enter the Raffle and", "compete for prizes"),
        arrayOf("Double your chances", "to win a prize with", "two Raffle Entries"),
        arrayOf("Triple your odds", "with three Raffle", "Entries in the drum"),
    )
    const val play = "Play"
    const val claim = "Claim ticket"
    const val winner = "WINNING ENTRY"
    const val entry = "0847"
    const val stubTop = "ENTRIES"
}

internal object RdsColor {
    val bgTop = Color(0xFF6C5590)
    val bgGlow = Color(0xFF8A63D2)
    val bgMid = Color(0xFF22104A)
    val bgDeep = Color(0xFF13023A)
    val bgLow = Color(0xFF020106)
    val bgFoot = Color(0xFF03041A)
    val violet = Color(0xFF6D3FF0)
    val violetHi = Color(0xFF9B7BFF)
    val violetDeep = Color(0xFF4A1AA8)
    val confetti = Color(0xFF7B4CF0)
    val confettiHi = Color(0xFFA58AFF)
    val card = Color(0xFF0F0A1C)
    val cardEdge = Color(0xFF2A2140)
    val textHi = Color(0xFFF1EEFA)
    val textCard = Color(0xFFBCB4CC)
    val textDim = Color(0xFF8C849E)
    val chipLine = Color(0xFF6E6880)
    val playTop = Color(0xFF6A1CD6)
    val playLow = Color(0xFF4A0CA8)
    val gold = Color(0xFFFFD27A)
}

internal object RdsLayout {
    const val closeX = 362f
    const val closeY = 88f
    const val closeD = 44f
    const val drumX = 205f
    const val drumY = 178f
    const val drumR = 70f
    const val titleBase = 386f
    const val titleW = 272f
    val sparkL = floatArrayOf(50f, 346f)
    val sparkR = floatArrayOf(345f, 388f)
    const val sub1Y = 431f
    const val sub2Y = 453f
    const val subSize = 15.5f
    const val chipsY = 492f
    const val cardY = 561f
    const val cardW = 167f
    const val cardH = 202f
    const val cardR = 18f
    const val cardGap = 11f
    const val cardX0 = 17f
    const val notchW = 58f
    const val notchD = 7f
    const val checkDx = 143f
    const val checkDy = 23f
    const val checkD = 24f
    const val ballDy = 60f
    const val textDx = 13f
    const val textDy = 124f
    const val textSize = 15f
    const val textLine = 18f
    const val playX = 17f
    const val playY = 789f
    const val playW = 368f
    const val playH = 48f
    const val revealX = 201f
    const val revealY = 430f
    const val revealR = 60f
}

internal val RDS_ICON_BALLS = arrayOf(
    arrayOf(floatArrayOf(84f, 28f)),
    arrayOf(floatArrayOf(55f, 24f), floatArrayOf(107f, 24f)),
    arrayOf(floatArrayOf(42f, 20f), floatArrayOf(83f, 20f), floatArrayOf(124f, 20f)),
)
