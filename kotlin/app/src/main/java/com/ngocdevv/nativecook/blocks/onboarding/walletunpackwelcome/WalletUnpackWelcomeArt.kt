package com.ngocdevv.nativecook.blocks.onboarding.walletunpackwelcome

import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Shader
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/*
 * The wallet, drawn in its own box (origin at its top left): a back panel, and a front pocket
 * with a scooped notch and a croc-scale grain; and the two notes that slide out between them.
 * Every length is in dp: the caller scales the canvas by the density first.
 */

internal const val WALLET_W = 210f
internal const val WALLET_H = 152f
private const val WALLET_RADIUS = 16f
/** The pocket's top edge, below the wallet's, and the notch scooped out of it. */
private const val POCKET_TOP = 24f
private const val NOTCH_W = 46f
private const val NOTCH_D = 13f
private const val NOTE_W = 170f
private const val NOTE_H = 60f
/** The notes' box: wide and tall enough for a note risen above the wallet. */
internal const val NOTES_W = 320f
internal const val NOTES_H = 440f

private val BackTop = Color(0xFF34412F)
private val BackBottom = Color(0xFF232D20)
private val Grain = Color(0xFF2A3526)
private val Tones = listOf(Color(0xFF3E4C39), Color(0xFF43523E), Color(0xFF394734))
private val NoteTop = Color(0xFFDCE6DA)
private val NoteBottom = Color(0xFFB5C6B6)

// The croc grain: rows of these heights; in each, scales 16 to 36 dp wide, each nudged up or
// down a little and shaded one of three tones, all from a fixed seed.
private val RowHeights = floatArrayOf(13f, 15f, 12f, 16f, 14f, 13f, 15f, 14f, 12f)
private const val SEED = 20261014L

private class Scale(val x: Float, val y: Float, val w: Float, val h: Float, val tone: Int)

/** Each scale in the wallet's box. Park–Miller gives the same numbers in every language. */
private val Scales: List<Scale> = run {
    var s = SEED
    fun next(): Double {
        s = s * 16807 % 2147483647
        return s / 2147483647.0
    }
    val out = mutableListOf<Scale>()
    var y = POCKET_TOP.toDouble()
    var row = 0
    while (y < WALLET_H) {
        val h = RowHeights[row % RowHeights.size].toDouble()
        var x = -next() * 14
        while (x < WALLET_W) {
            val w = 16 + next() * 20
            val nudge = (next() - 0.5) * 3
            out += Scale(x.toFloat(), (y + nudge).toFloat(), w.toFloat(), (h - abs(nudge)).toFloat(), floor(next() * 3).toInt())
            x += w
        }
        y += h
        row += 1
    }
    out
}

private val WalletShape = Path().apply {
    addRoundRect(RoundRect(0f, 0f, WALLET_W, WALLET_H, CornerRadius(WALLET_RADIUS)))
}

/** The pocket's top edge with its notch, [dy] below where it sits. */
private fun Path.pocketEdge(dy: Float = 0f) {
    val mid = WALLET_W / 2
    val half = NOTCH_W / 2
    val top = POCKET_TOP + dy
    lineTo(mid - half, top)
    // 0.55 of the half-axes puts the cubic's handles where a quarter ellipse needs them.
    cubicTo(mid - half, top + NOTCH_D * 0.55f, mid - half * 0.55f, top + NOTCH_D, mid, top + NOTCH_D)
    cubicTo(mid + half * 0.55f, top + NOTCH_D, mid + half, top + NOTCH_D * 0.55f, mid + half, top)
    lineTo(WALLET_W, top)
}

/** The pocket: the wallet's lower part, its top edge scooped in the middle. */
private val PocketShape = Path().apply {
    val r = WALLET_RADIUS
    moveTo(0f, POCKET_TOP)
    pocketEdge()
    lineTo(WALLET_W, WALLET_H - r)
    quadraticTo(WALLET_W, WALLET_H, WALLET_W - r, WALLET_H)
    lineTo(r, WALLET_H)
    quadraticTo(0f, WALLET_H, 0f, WALLET_H - r)
    close()
}

private val PocketEdge = Path().apply {
    moveTo(0f, POCKET_TOP + 0.5f)
    pocketEdge(0.5f)
}

/** The back panel. */
internal fun DrawScope.drawWalletBack() {
    drawPath(WalletShape, Brush.verticalGradient(listOf(BackTop, BackBottom), 0f, WALLET_H))
    // A soft light along the top edge.
    drawLine(Color.White.copy(alpha = 0.1f), Offset(WALLET_RADIUS, 0.8f), Offset(WALLET_W - WALLET_RADIUS, 0.8f), strokeWidth = 1f)
}

/** The pocket with its grain, shading and lit edge, and the shadow its edge casts above it. */
internal fun DrawScope.drawWalletPocket() {
    // The shadow of the pocket's edge, over whatever is tucked in behind it.
    clipPath(WalletShape) {
        drawRect(
            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0f), Color.Black.copy(alpha = 0.28f)), POCKET_TOP - 7, POCKET_TOP + 1),
            Offset(0f, POCKET_TOP - 7),
            Size(WALLET_W, 8 + NOTCH_D),
        )
    }
    clipPath(PocketShape) {
        drawRect(Grain, Offset.Zero, Size(WALLET_W, WALLET_H))
        for (scale in Scales) {
            drawRoundRect(Tones[scale.tone], Offset(scale.x + 1, scale.y + 1), Size(scale.w - 2, scale.h - 2), CornerRadius(5f))
            // Each scale catches a little light along its top.
            drawRoundRect(Color.White.copy(alpha = 0.05f), Offset(scale.x + 3, scale.y + 1.5f), Size(scale.w - 6, 2f), CornerRadius(1f))
        }
        // Light from above, falling off towards the bottom.
        drawRect(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Black.copy(alpha = 0.22f)), POCKET_TOP, WALLET_H),
            Offset.Zero,
            Size(WALLET_W, WALLET_H),
        )
    }
    // The pocket's lit edge.
    drawPath(PocketEdge, Color.White.copy(alpha = 0.16f), style = Stroke(1f))
}

/** A note centred at ([cx], [cy]), turned, its long edges rippling, softened by [blur] (a sigma). */
private fun DrawScope.drawNote(cx: Float, cy: Float, turn: Float, wave: Float, phase: Float, blur: Float) {
    val half = NOTE_W / 2
    val outline = android.graphics.Path()
    // The top edge left to right, then the bottom edge back, ten dp apart.
    var x = -half
    while (x <= half) {
        val y = -NOTE_H / 2 + wave * sin((x + half) / 120 * 2 * PI.toFloat() + phase)
        if (x == -half) outline.moveTo(x, y) else outline.lineTo(x, y)
        x += 10
    }
    x = half
    while (x >= -half) {
        outline.lineTo(x, NOTE_H / 2 + wave * sin((x + half) / 120 * 2 * PI.toFloat() + phase + 0.6f))
        x -= 10
    }
    outline.close()

    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        // The blur mask scales with the canvas, so its radius is in dp like everything here.
        val soft = BlurMaskFilter(sigmaToRadius(blur), BlurMaskFilter.Blur.NORMAL)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            maskFilter = soft
            shader = LinearGradient(0f, -NOTE_H / 2, 0f, NOTE_H / 2, NoteTop.toArgb(), NoteBottom.toArgb(), Shader.TileMode.CLAMP)
        }
        native.save()
        native.translate(cx, cy)
        native.rotate(turn)
        native.drawPath(outline, paint)
        // A paler oval where a note would carry its watermark, and a fine inner border.
        paint.shader = null
        paint.color = Color.White.copy(alpha = 0.35f).toArgb()
        native.drawOval(-half + 22, -16f, -half + 62, 16f, paint)
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.White.copy(alpha = 0.4f).toArgb()
        native.drawRect(-half + 6, -NOTE_H / 2 + 6, half - 6, NOTE_H / 2 - 6, paint)
        native.restore()
    }
}

/** Both notes in the notes' box, whose centre is the wallet's. */
internal fun DrawScope.drawNotes(h: WalletHero) {
    drawNote(NOTES_W / 2, NOTES_H / 2 + h.note1Y, h.note1Turn, h.note1Wave, h.phase, h.note1Blur)
    drawNote(NOTES_W / 2, NOTES_H / 2 + h.note2Y, h.note2Turn, h.note2Wave, h.phase + 1.3f, h.note2Blur)
}
