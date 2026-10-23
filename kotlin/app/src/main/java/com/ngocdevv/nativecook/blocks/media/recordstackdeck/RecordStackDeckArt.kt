package com.ngocdevv.nativecook.blocks.media.recordstackdeck

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/** Ten invented albums with covers drawn in code, and the record that slides out of a sleeve. Ported from RecordStackDeckArt.swift. */
internal class DeckAlbum(val title: String, val artist: String, val glow: Color)

internal val deckAlbums = listOf(
    DeckAlbum("Afterglow Arcade", "Mina Sol", Color(0xFFFF5A36)),
    DeckAlbum("Blue Static", "The Low Hours", Color(0xFF2747FF)),
    DeckAlbum("Lime Hours", "Kite Club", Color(0xFFB6F23A)),
    DeckAlbum("Velvet Exit", "Ora Vance", Color(0xFFC23BFF)),
    DeckAlbum("Salt & Cedar", "Fen Harlow", Color(0xFFE8B98A)),
    DeckAlbum("Night Swim", "Coral Hum", Color(0xFF1FB5C9)),
    DeckAlbum("Chroma Rings", "Halcyon Vale", Color(0xFF8A7CFF)),
    DeckAlbum("Pink Noise", "Dot Mercer", Color(0xFFFF7EB6)),
    DeckAlbum("Hour Twenty-Four", "Sunday Static", Color(0xFFF5C431)),
    DeckAlbum("White Room Tapes", "Lune Archive", Color(0xFFDADAD6)),
)

private val typePaint = Paint(Paint.ANTI_ALIAS_FLAG)

private fun weighted(weight: Int): Typeface =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.DEFAULT, weight, false) else Typeface.DEFAULT_BOLD

private val black = weighted(900)
private val heavy = weighted(800)
private val bold = weighted(700)

/** Text whose line box's top-left (or centre) is at (x, y), as SwiftUI places it. */
private fun DrawScope.type(text: String, size: Float, color: Color, x: Float, y: Float, center: Boolean = false, face: Typeface = black) {
    val p = typePaint
    p.typeface = face
    p.textSize = size
    p.letterSpacing = -0.04f
    p.color = color.toArgb()
    p.textAlign = if (center) Paint.Align.CENTER else Paint.Align.LEFT
    val fm = p.fontMetrics
    val baseline = if (center) y - (fm.ascent + fm.descent) / 2 else y - fm.ascent
    drawContext.canvas.nativeCanvas.drawText(text, x, baseline, p)
}

/** Draws album i's cover in the square (0, 0, s, s). */
internal fun DrawScope.deckCover(i: Int, s: Float) {
    clipRect(0f, 0f, s, s) {
        val full = Size(s, s)
        when (i) {
            0 -> { // Afterglow Arcade: a striped sun setting into the bottom edge.
                drawRect(Color(0xFFFF5A36), size = full)
                clipPath(Path().apply { addOval(Rect(Offset(s * 0.5f, s * 0.86f), s * 0.42f)) }) {
                    drawRect(Color(0xFFFFD23F), size = full)
                    for (k in 0 until 6) {
                        drawRect(Color(0xFFFF5A36), Offset(0f, s * (0.6f + k * 0.07f)), Size(s, s * (0.012f + k * 0.006f)))
                    }
                }
                type("AFTERGLOW", s * 0.1f, Color(0xFF3A0D05), s * 0.07f, s * 0.07f)
            }
            1 -> { // Blue Static: a dot screen with one orange dot out of line.
                drawRect(Color(0xFF1F3FFF), size = full)
                for (row in 0 until 9) for (col in 0 until 9) {
                    val r = s * (0.012f + 0.012f * ((row + col) % 3))
                    drawCircle(Color.White.copy(alpha = 0.85f), r, Offset(s * (0.1f + col * 0.1f), s * (0.1f + row * 0.1f)))
                }
                drawCircle(Color(0xFFFF8A2A), s * 0.07f, Offset(s * 0.72f, s * 0.34f))
            }
            2 -> { // Lime Hours: lowercase type, very large.
                drawRect(Color(0xFFB6F23A), size = full)
                type("lime", s * 0.42f, Color(0xFF111111), s * 0.5f, s * 0.56f, center = true)
                type("hours", s * 0.07f, Color(0xFF111111), s * 0.08f, s * 0.86f, face = bold)
            }
            3 -> { // Velvet Exit: a glowing pink orb on plum.
                drawRect(Color(0xFF2A0F3D), size = full)
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0xFFFF9AD5), Color(0xFFC23BFF), Color(0x00C23BFF)),
                        center = Offset(s * 0.42f, s * 0.46f), radius = s * 0.42f,
                    ),
                    size = full,
                )
                drawCircle(Color.White.copy(alpha = 0.5f), s * 0.3f, Offset(s * 0.42f, s * 0.46f), style = Stroke(max(1f, s * 0.006f)))
                type("VELVET EXIT", s * 0.06f, Color(0xFFF6D8FF), s * 0.07f, s * 0.86f, face = heavy)
            }
            4 -> { // Salt & Cedar: three diagonal bands on cream.
                drawRect(Color(0xFFEFE6D2), size = full)
                listOf(Color(0xFF1E6B66), Color(0xFF7A4A2B), Color(0xFFC8553D)).forEachIndexed { k, color ->
                    val o = s * (0.15f + k * 0.22f)
                    val band = Path().apply {
                        moveTo(o, s)
                        lineTo(o + s * 0.14f, s)
                        lineTo(o + s * 0.64f, 0f)
                        lineTo(o + s * 0.5f, 0f)
                        close()
                    }
                    drawPath(band, color)
                }
            }
            5 -> { // Night Swim: wavy lines under a moon.
                drawRect(Brush.verticalGradient(listOf(Color(0xFF0B4A5E), Color(0xFF041C26)), startY = 0f, endY = s), size = full)
                drawCircle(Color(0xFFE6FBFF), s * 0.08f, Offset(s * 0.72f, s * 0.24f))
                for (k in 0 until 7) {
                    val y0 = s * (0.48f + k * 0.07f)
                    val wave = Path().apply {
                        moveTo(0f, y0)
                        for (j in 0..24) {
                            val x = s * j / 24f
                            lineTo(x, y0 + (sin(x / s * 4 * PI + k) * s * 0.018).toFloat())
                        }
                    }
                    drawPath(wave, Color(0xFF5FE1EE).copy(alpha = 0.9f - k * 0.1f), style = Stroke(max(1f, s * 0.01f)))
                }
            }
            6 -> { // Chroma Rings: three offset rings, like a misregistered print.
                drawRect(Color(0xFF0D0D10), size = full)
                listOf(Color(0xFFFF3B5C), Color(0xFF3BFF9E), Color(0xFF3B7CFF)).forEachIndexed { k, color ->
                    val dx = (k - 1) * s * 0.045f
                    drawCircle(color, s * 0.26f, Offset(s * 0.5f + dx, s * 0.5f - dx * 0.4f), style = Stroke(s * 0.05f), blendMode = BlendMode.Screen)
                }
            }
            7 -> { // Pink Noise: a checkerboard floor under a pink sky.
                drawRect(Color(0xFFFF7EB6), size = full)
                val cell = s / 8
                for (row in 0 until 4) for (col in 0 until 8) {
                    if ((row + col) % 2 == 0) drawRect(Color(0xFF16161A), Offset(col * cell, s * 0.5f + row * cell), Size(cell, cell))
                }
                type("pink noise", s * 0.09f, Color(0xFF16161A), s * 0.07f, s * 0.08f, face = heavy)
            }
            8 -> { // Hour Twenty-Four: big numerals.
                drawRect(Color(0xFFF5C431), size = full)
                type("24", s * 0.62f, Color(0xFF14110A), s * 0.52f, s * 0.5f, center = true)
                drawRect(Color(0xFF14110A), Offset(s * 0.07f, s * 0.86f), Size(s * 0.3f, s * 0.02f))
            }
            else -> { // White Room Tapes: one thin circle and a red dot.
                drawRect(Color(0xFFF4F4F2), size = full)
                drawCircle(Color(0xFF16161A), s * 0.28f, Offset(s * 0.5f, s * 0.48f), style = Stroke(max(1f, s * 0.006f)))
                drawCircle(Color(0xFFE53935), s * 0.035f, Offset(s * 0.7f, s * 0.3f))
            }
        }
        // The sleeve's printed edge and a faint sheen.
        drawRect(
            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0f), Color.Black.copy(alpha = 0.12f)), Offset.Zero, Offset(s, s)),
            size = full,
        )
        drawRect(Color.Black.copy(alpha = 0.25f), topLeft = Offset(0.5f, 0.5f), size = Size(s - 1, s - 1), style = Stroke(1f))
    }
}

/** The record: black vinyl with grooves, a gloss fixed to the light, and the cover as its label. */
internal fun DrawScope.deckVinyl(album: Int, c: Offset, r: Float, angle: Float) {
    drawCircle(Color(0xFF0B0B0D), r, c)
    val label = r * 0.36f
    for (k in 0 until 30) {
        val rad = label + 6 + (r - label - 10) * k / 29f
        drawCircle(Color.White.copy(alpha = if (k % 2 == 0) 0.04f else 0.07f), rad, c, style = Stroke(0.7f))
    }
    drawCircle(Color.White.copy(alpha = 0.14f), r - 0.75f, c, style = Stroke(1.5f))
    val ring = Path().apply {
        fillType = PathFillType.EvenOdd
        addOval(Rect(c, r - 2))
        addOval(Rect(c, label + 2))
    }
    rotate(-60f, c) {
        drawPath(
            ring,
            Brush.sweepGradient(
                0f to Color.White.copy(alpha = 0f), 0.08f to Color.White.copy(alpha = 0.14f), 0.18f to Color.White.copy(alpha = 0f),
                0.5f to Color.White.copy(alpha = 0f), 0.58f to Color.White.copy(alpha = 0.1f), 0.68f to Color.White.copy(alpha = 0f),
                1f to Color.White.copy(alpha = 0f), center = c,
            ),
        )
    }
    translate(c.x, c.y) {
        rotate(angle, Offset.Zero) {
            clipPath(Path().apply { addOval(Rect(Offset.Zero, label)) }) {
                translate(-label, -label) { deckCover(album, label * 2) }
            }
        }
    }
    drawCircle(Color(0xFFD8D8DC), 3.5f, c)
    drawCircle(Color(0xFF222226), 1.4f, c)
}
