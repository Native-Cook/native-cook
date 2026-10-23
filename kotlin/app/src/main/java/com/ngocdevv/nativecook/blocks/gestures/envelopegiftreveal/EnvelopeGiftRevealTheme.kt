package com.ngocdevv.nativecook.blocks.gestures.envelopegiftreveal

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.round

internal object EnvelopeGiftRevealColors {
    val table = Color(0xFF1D1B18)
    val ink = Color(0xFFF3ECDF)
    val mute = Color(0x94F3ECDF)
    val faint = Color(0x5CF3ECDF)
    val paper = Color(0xFFEFE6D5)
    val paperHigh = Color(0xFFF7F0E2)
    val paperLow = Color(0xFFDCCFB8)
    val paperInside = Color(0xFFCDBFA5)
    val paperShade = Color(0x38843E1E)
    val crease = Color(0x4D604A28)
    val pen = Color(0xFF22314B)
    val wax = Color(0xFF7C1E22)
    val waxHigh = Color(0xFFB23A3A)
    val waxLow = Color(0xFF4C0F13)
    val card = Color(0xFFF6F0E4)
    val cardEdge = Color(0x2E503C1E)
    val cardInk = Color(0xFF2A2219)
    val cardMute = Color(0x8F2A2219)
    val letterpress = Color(0xFF2E4A3C)
    val shadow = Color(0x8C000000)
    val cta = Color(0xFFF3ECDF)
    val ctaInk = Color(0xFF1D1B18)
}

internal object EnvelopeGiftRevealSpace {
    const val xs = 4f
    const val sm = 8f
    const val md = 12f
    const val lg = 16f
    const val side = 20f
    const val xl = 24f
    const val xxl = 32f
}

internal object EnvelopeGiftRevealLayout {
    const val side = 24f
    const val headline = 44f
    const val headlineLine = 48f
    const val envW = 336f
    const val envH = 232f
    const val sealD = 56f
    const val sealY = 0.62f
    const val flapTip = 0.66f
    const val cardW = 340f
    const val cardH = 214f
    const val cardR = 8f
    const val cardInScale = 0.92f
    const val title = 40f
    const val titleLine = 44f
    const val hintH = 52f
    const val buttonH = 52f
}

internal object EnvelopeGiftRevealGift {
    const val amount = "\$50"
    const val shop = "Fable & Fern"
    const val shopLine = "BOOKSELLERS · EST. 1987"
    const val kicker = "FROM NORA · 14 OCTOBER"
    const val headline = "Something\nfor you"
    const val title = "From Nora,\nwith love"
    const val to = "for Sam"
    val note = listOf(
        "Sam —",
        "for the long train rides.",
        "Pick something you",
        "would never buy yourself.",
        "— Nora x",
    )
    const val caption = "\$50 at Fable & Fern · in store or online"
    const val captionSub = "Valid until 14 October 2027"
    const val cta = "Add to Wallet"
    const val hint = "Lift the flap"
    const val message = "Sam, for the long train rides. Pick something you would never buy yourself. Nora"
}

internal data class EnvelopeGiftRevealScreenLayout(
    val headlineTop: Float,
    val envTop: Float,
    val buttonY: Float,
    val titleTop: Float,
    val cardTop: Float,
    val messageTop: Float,
    val ctaTop: Float,
)

internal fun envelopeGiftRevealScreenLayout(
    height: Float,
    top: Float,
    bottom: Float,
): EnvelopeGiftRevealScreenLayout {
    val headlineTop = top + 40f
    val envTop = round(height * 0.5f - EnvelopeGiftRevealLayout.envH / 2f + 24f)
    val foot = height - max(bottom, 16f) - 16f
    val buttonY = foot - EnvelopeGiftRevealLayout.hintH / 2f
    val titleTop = headlineTop
    val ctaTop = foot - EnvelopeGiftRevealLayout.buttonH
    val titleBottom = titleTop + EnvelopeGiftRevealLayout.titleLine * 2f
    val cardTop = round(
        (titleBottom + ctaTop) / 2f - (EnvelopeGiftRevealLayout.cardH + 60f) / 2f,
    )
    val messageTop = cardTop + EnvelopeGiftRevealLayout.cardH + 24f
    return EnvelopeGiftRevealScreenLayout(
        headlineTop = headlineTop,
        envTop = envTop,
        buttonY = buttonY,
        titleTop = titleTop,
        cardTop = cardTop,
        messageTop = messageTop,
        ctaTop = ctaTop,
    )
}

internal object EnvelopeGiftRevealAssets {
    const val root = "envelopegiftreveal"
    const val serif = "$root/envelopegiftreveal_InstrumentSerif-Regular.ttf"
    const val hand = "$root/envelopegiftreveal_CoveredByYourGrace.ttf"
    const val ui = "$root/envelopegiftreveal_Inter-Medium.ttf"
    const val uiBold = "$root/envelopegiftreveal_Inter-SemiBold.ttf"
}
