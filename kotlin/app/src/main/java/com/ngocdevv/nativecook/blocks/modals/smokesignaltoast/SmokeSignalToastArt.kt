package com.ngocdevv.nativecook.blocks.modals.smokesignaltoast

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.ceil
import kotlin.math.max

/*
 The message the smoke forms, baked once (off the main thread) into two masks: a sharp one and a
 blurred one (the shader blends from the blurred one while the words condense and to it again as
 they tear). As in smokeArt.ts the message line is sized down from 27 until it fits the screen.

 Unlike the React Native version the masks cover only a band of the screen around the words (full
 width, the words plus a transparent margin above and below) instead of the whole screen: the
 shader samples them with a clamped bitmap shader, whose transparent edge rows stand in for the
 rest of the screen.
*/

internal const val SMOKE_SENDER = "KAI · LAKESIDE CREW"
internal const val SMOKE_MESSAGE = "Dinner's ready at the lake"

/** Pixels per dp of the baked masks, as in React Native. */
internal const val SMOKE_ART_SCALE = 2f
/** Transparent margin above and below the words, in dp (more than the blur reaches). */
private const val MARGIN = 48f

internal class SmokeArt(
    val sharp: Bitmap,
    val soft: Bitmap,
    /** Where the band starts on the screen (dp). */
    val bandTop: Float,
    /** The touch area of the message (dp). */
    val boxX: Float,
    val boxY: Float,
    val boxW: Float,
    val boxH: Float,
)

private fun weighted(weight: Int): Typeface =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.SANS_SERIF, weight, false) else Typeface.DEFAULT_BOLD

internal fun bakeSmokeMessage(w: Float, centerY: Float): SmokeArt {
    val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = weighted(700)
        textSize = 13f
        color = Color.WHITE
    }
    var size = 27f
    val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = weighted(600)
        textSize = size
        color = Color.WHITE
    }
    val room = w - 56f
    while (big.measureText(SMOKE_MESSAGE) > room && size > 18f) {
        size -= 1f
        big.textSize = size
    }
    val bw = big.measureText(SMOKE_MESSAGE)
    val sw = small.measureText(SMOKE_SENDER)
    val top = centerY - (13f + 10f + size) / 2f
    val senderBase = top + 12f
    val messageBase = top + 13f + 10f + size * 0.8f
    val bandTop = top - MARGIN
    val bandH = 13f + 10f + size + MARGIN * 2f

    fun render(sigmaDp: Float): Bitmap {
        val pw = max(1, ceil(w * SMOKE_ART_SCALE).toInt())
        val ph = max(1, ceil(bandH * SMOKE_ART_SCALE).toInt())
        val bitmap = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(SMOKE_ART_SCALE, SMOKE_ART_SCALE)
        canvas.translate(0f, -bandTop)
        // The blur's sigma is in dp, like Skia's blur that respects the canvas scale.
        val filter = BlurMaskFilter(sigmaToRadius(sigmaDp * SMOKE_ART_SCALE), BlurMaskFilter.Blur.NORMAL)
        small.maskFilter = filter
        big.maskFilter = filter
        canvas.drawText(SMOKE_SENDER, (w - sw) / 2f, senderBase, small)
        canvas.drawText(SMOKE_MESSAGE, (w - bw) / 2f, messageBase, big)
        small.maskFilter = null
        big.maskFilter = null
        return bitmap
    }

    val sharp = render(0.4f)
    val soft = render(7f)
    val wide = max(bw, sw)
    return SmokeArt(
        sharp = sharp,
        soft = soft,
        bandTop = bandTop,
        boxX = (w - wide) / 2f - 16f,
        boxY = top - 16f,
        boxW = wide + 32f,
        boxH = size + 23f + 32f,
    )
}
