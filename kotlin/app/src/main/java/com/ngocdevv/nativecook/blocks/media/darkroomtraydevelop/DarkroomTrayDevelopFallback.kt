package com.ngocdevv.nativecook.blocks.media.darkroomtraydevelop

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.min
import kotlin.math.roundToInt

// Below Android 13 there is no AGSL: the scene is drawn from shapes and the photo itself. The bench,
// the tray and its ribs, and the tongs are flat gradients; each sheet is paper white with the photo
// over it as a contrasty black-and-white print whose opacity follows its development (so the image
// comes up evenly, not shadows first); the liquid is a sheen band that slides with the tilt and
// stripes along the wave's crests. The safelight multiplies the whole scene at the end (an opaque
// colour, so Modulate keeps the alpha).

/** Contrasty black and white, as the shader's print curve: lum → (lum − 0.06) × 1.18. */
private val PrintMatrix = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0.354f, 0.696f, 0.13f, 0f, -18f,
            0.354f, 0.696f, 0.13f, 0f, -18f,
            0.354f, 0.696f, 0.13f, 0f, -18f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

internal fun DrawScope.darkroomFallback(width: Float, height: Float, l: DarkroomLayout, f: DarkroomFrame, photo: ImageBitmap?) {
    val (tx, ty, tw, th) = l.tray
    // The bench, lit from above.
    drawRect(Color(0.05f, 0.048f, 0.046f), Offset.Zero, Size(width, height))
    drawRect(
        Brush.radialGradient(listOf(Color(0.32f, 0.3f, 0.29f, 0.8f), Color(0.32f, 0.3f, 0.29f, 0f)), Offset(width * 0.5f, -height * 0.12f), width * 1.1f),
        Offset.Zero,
        Size(width, height),
    )
    // The tray's shadow, rim and floor.
    drawRoundRect(Color(0f, 0f, 0f, 0.45f), Offset(tx - 4f, ty + 6f), Size(tw + 8f, th + 12f), CornerRadius(30f))
    drawRoundRect(Color(0.3f, 0.3f, 0.31f), Offset(tx, ty), Size(tw, th), CornerRadius(26f))
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0.55f, 0.55f, 0.56f), Color(0.3f, 0.3f, 0.31f)), ty, ty + 30f),
        Offset(tx + 6f, ty + 6f),
        Size(tw - 12f, th - 12f),
        CornerRadius(20f),
    )
    val ix = tx + 14f
    val iy = ty + 14f
    val iw = tw - 28f
    val ih = th - 28f
    drawRoundRect(Color(0.24f, 0.24f, 0.25f), Offset(ix, iy), Size(iw, ih), CornerRadius(16f))
    clipRect(ix, iy, ix + iw, iy + ih) {
        var y = ty
        while (y < iy + ih) {
            if (y > iy) drawRect(Color(0.14f, 0.14f, 0.15f), Offset(ix, y + 6f), Size(iw, 6f))
            y += 18f
        }
        // The two sheets, shifted a little by the liquid's slope.
        val slope = -f.rock * 10f / (tw * 0.5f)
        val shift = slope * 140f
        darkroomSheet(l, f.slideA + shift, f.dev, photo)
        darkroomSheet(l, f.slideB + shift, f.devB, photo)
        // The liquid: a stop-bath tint, the sheen band sliding with the tilt, the wave's crests.
        if (f.stopBath > 0f) drawRect(Color(1f, 0.86f, 0.55f, 0.18f * f.stopBath), Offset(ix, iy), Size(iw, ih))
        val bandX = l.tray[0] + tw / 2f - slope * 1400f
        drawRect(
            Brush.horizontalGradient(
                listOf(Color(1f, 0.97f, 0.94f, 0f), Color(1f, 0.97f, 0.94f, 0.18f), Color(1f, 0.97f, 0.94f, 0f)),
                bandX - tw * 0.2f,
                bandX + tw * 0.2f,
            ),
            Offset(ix, iy),
            Size(iw, ih),
        )
        for (n in -2..2) {
            // Crests where sin(u · 4.2 − phase) peaks, u from −1 to 1 across the tray.
            val u = (PI_HALF + 2f * PI_F * n + f.wphase % (2f * PI_F)) / 4.2f
            if (u < -1.05f || u > 1.05f) continue
            val x = tx + tw / 2f + u * tw / 2f
            drawRect(
                Brush.horizontalGradient(listOf(Color(1f, 1f, 1f, 0f), Color(1f, 1f, 1f, 0.03f + 0.2f * f.wave), Color(1f, 1f, 1f, 0f)), x - 8f, x + 8f),
                Offset(x - 8f, iy),
                Size(16f, ih),
            )
        }
    }
    // The tongs on the rim.
    rotateRad(0.62f, Offset(tx + tw - 58f, ty + 26f)) {
        val o = Offset(tx + tw - 58f - 66f, ty + 26f)
        drawRoundRect(Color(0.66f, 0.5f, 0.31f), o + Offset(0f, -9.5f), Size(132f, 9f), CornerRadius(4f))
        drawRoundRect(Color(0.7f, 0.54f, 0.34f), o + Offset(0f, 0.5f), Size(132f, 9f), CornerRadius(4f))
    }
    // The safelight, then the room light.
    val light = f.light
    val lightCol = Color(0.78f + 0.22f * light, 0.1326f + (0.985f - 0.1326f) * light, 0.0858f + (0.965f - 0.0858f) * light)
    drawRect(lightCol, Offset.Zero, Size(width, height), blendMode = BlendMode.Modulate)
    drawRect(
        Brush.radialGradient(listOf(Color(1f, 0.17f, 0.11f, 0.12f * (1f - light)), Color(1f, 0.17f, 0.11f, 0f)), Offset(width * 0.5f, -height * 0.12f), height * 0.45f),
        Offset.Zero,
        Size(width, height),
    )
}

private const val PI_F = 3.1415927f
private const val PI_HALF = 1.5707964f

/** One sheet at offset dx: paper white, the print over it at its development. */
private fun DrawScope.darkroomSheet(l: DarkroomLayout, dx: Float, dev: Float, photo: ImageBitmap?) {
    val (cx, cy, sw, sh) = l.sheet
    if (cx + dx + sw < 0f || cx + dx - sw > size.width / density + 40f) return
    rotateRad(-0.026f + dx * 0.0004f, Offset(cx + dx, cy)) {
        val left = cx + dx - sw / 2f
        val top = cy - sh / 2f
        drawRoundRect(Color(0.95f, 0.94f, 0.915f), Offset(left, top), Size(sw, sh), CornerRadius(2f))
        if (photo != null) {
            val bx = sw * 0.06f
            val by = sh * 0.05f
            drawImage(
                photo,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(photo.width, photo.height),
                dstOffset = IntOffset((left + bx).roundToInt(), (top + by).roundToInt()),
                dstSize = IntSize((sw - 2f * bx).roundToInt(), (sh - 2f * by).roundToInt()),
                alpha = min(1f, 0.25f + 0.75f * dev) * min(1f, dev / 0.14f),
                colorFilter = PrintMatrix,
            )
        }
    }
}
