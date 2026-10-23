package com.ngocdevv.nativecook.blocks.gestures.envelopegiftreveal

import androidx.compose.ui.graphics.Color
import com.ngocdevv.nativecook.shared.rgba
import kotlin.math.max

/** Colours measured from the source (theme.ts COLORS). */
internal object EnvelopeColors {
    val lime = Color(0xFFDAFF1C)
    val limeHigh = Color(0xFFF0FF72)
    val limeLow = Color(0xFFCCFA14)
    val limeEdge = Color(0xFFB2DB00)
    val limeInside = Color(0xFFA9CF00)

    /** Envelope paper, sampled from the source frame. */
    val flapHigh = Color(0xFFEAFF66)
    val flapLow = Color(0xFFCEFF14)
    val sideHigh = Color(0xFFDFFF30)
    val sideLow = Color(0xFFD5FF1B)
    val sideRight = Color(0xFFCBFF0D)
    val bottomHigh = Color(0xFFD4FF22)
    val bottomLow = Color(0xFFCCFF18)
    val crease = rgba(120, 160, 0, 0.28f)
    val ink = Color(0xFF0A0A0A)
    val white = Color(0xFFFFFFFF)
    val sealMark = Color(0xFFD4FF1C)
    val message = Color(0xFFFFFFFF)
    val messageInk = Color(0xFF151515)
    val shadow = rgba(60, 80, 0, 0.2f)
    val cardPink = Color(0xFFF2A9BC)
}

/** Spacing scale copied from theme.ts SPACE. */
internal object EnvelopeSpace {
    const val xs = 4f
    const val sm = 8f
    const val md = 12f
    const val lg = 16f
    const val side = 20f
    const val xl = 24f
    const val xxl = 32f
}

/** Layout measured from the source, 402 dp wide (theme.ts LAYOUT). */
internal object EnvelopeLayout {
    const val side = 33f

    /** Wide extended black face (Archivo Expanded, OFL), sized as measured on the reference. */
    const val headline = 54f
    const val headlineLine = 50f
    const val headlineTop = 112f
    const val title = 38f
    const val titleLine = 36f
    const val envW = 336f
    const val envH = 258f
    const val envTop = 260f
    const val sealD = 60f
    const val sealY = 0.66f
    const val flapTip = 0.7f

    /** About 40 % of the screen width, as in the source. */
    const val buttonD = 134f
    const val buttonCentre = 638f
    const val cardW = 360f
    const val cardH = 186f
    const val cardR = 14f
    const val cardInScale = 0.9f
    const val titleGap = 30f
    const val finalTop = 125f
    const val finalSide = EnvelopeSpace.side
    const val cardGap = 106f
    const val ctaGap = 34f
    const val messageH = 125f
    const val buttonH = 52f
}

/** Copy of the gift (theme.ts GIFT). */
internal object EnvelopeGift {
    const val amount = "\$25"
    const val value = 25
    const val message = "Happy spring! From all of us at Petal :)"
    const val cta = "Start shopping"
    const val title = "gift\ncard"
    const val headline = "wow,\na gift!"
}

/** Vertical positions for one screen height (theme.ts screenLayout). */
internal class EnvelopeScreenLayout(
    val headlineTop: Float,
    val envTop: Float,
    val buttonY: Float,
    val titleTop: Float,
    val cardTop: Float,
    val messageTop: Float,
    val ctaTop: Float,
)

/**
 * Sealed: headline under the status bar, envelope under it, the button centred in the space left
 * below. Revealed: title, card, message and button as one group placed as measured on the reference.
 */
internal fun envelopeScreenLayout(height: Float, top: Float): EnvelopeScreenLayout {
    // Measured on the reference for an 874 dp screen; taller screens keep it centred.
    val shift = (height - 874f) / 2f
    val headlineTop = max(top + 8f, EnvelopeLayout.headlineTop + shift)
    val envTop = EnvelopeLayout.envTop + shift
    val buttonY = EnvelopeLayout.buttonCentre + shift
    // Measured on the reference (title ~160 dp from the top on an 874 dp screen); centred on taller ones.
    val titleTop = max(top + 8f, EnvelopeLayout.finalTop + (height - 874f) / 2f)
    val cardTop = titleTop + EnvelopeLayout.titleLine * 2f + EnvelopeLayout.titleGap
    val messageTop = cardTop + EnvelopeLayout.cardH + EnvelopeLayout.cardGap
    val ctaTop = messageTop + EnvelopeLayout.messageH + EnvelopeLayout.ctaGap
    return EnvelopeScreenLayout(headlineTop, envTop, buttonY, titleTop, cardTop, messageTop, ctaTop)
}
